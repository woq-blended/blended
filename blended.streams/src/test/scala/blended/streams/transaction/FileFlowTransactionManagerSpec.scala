package blended.streams.transaction

import java.io.File
import java.lang.management.{ManagementFactory, OperatingSystemMXBean}
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.testkit.TestKit
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.streams.worklist.WorklistStateStarted
import blended.testsupport.retry.ResultPoller
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.util.logging.Logger
import com.sun.management.UnixOperatingSystemMXBean
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

trait FTMFactory {
  def createTransactionManager(dir : String) : FlowTransactionManager =
    createTransactionManager(FlowTransactionManagerConfig(new File(BlendedTestSupport.projectTestOutput, dir)))

  def createTransactionManager(cfg : FlowTransactionManagerConfig) : FlowTransactionManager
}

trait FlowTransactionManagerSpec
  extends LoggingFreeSpecLike
  with Matchers
  with ScalaCheckPropertyChecks { this : FTMFactory =>

  val actorSys : ActorSystem

  val pollInterval : FiniteDuration = 100.millis
  val timeout : FiniteDuration = 10.seconds

  private def updateTest[T](ftm : FlowTransactionManager, event : FlowTransactionEvent)(f : FlowTransaction => T) : T = {
    ftm.updateTransaction(event) match {
      case Success(t) => f(t)
      case Failure(e) => fail(e)
    }
  }

  "The transaction manager should" - {

    "create a new transaction for a Transaction Started event" in {

      val tMgr : FlowTransactionManager = createTransactionManager("create")

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          (new ResultPoller[Option[FlowTransaction]](actorSys, timeout, s"${getClass().getSimpleName()} - createTransaction")(() => tMgr.findTransaction(t.tid)).execute {
            case None =>
            case Some(v) => assert(t === v)
          }).isSuccess should be (true)
        }
      }
    }

    // In rare occasion it is possible that an inbound component (i.e. an inbound JMS bridge) generates a transaction
    // started event which is processed asynchronously by the transaction manager. If a subsequent component starts
    // to further process and updates the transaction before (!!) the initial start event could be processed, the
    // downstream never sees a FlowtransactionStarted event, but only events of the updated state (i.e Failed or
    // completed.
    // Therefore we will flag the first transaction we see for a given id, so that a downstream processor can
    // take appropriate action.
    "Mark the the first transaction event it sees for a given id regardless the transaction state" in logException {
      val tMgr : FlowTransactionManager = createTransactionManager("first")

      // Generate a bunch of transactions and change the state to any state but Started
      val tGen = for {
        t <- FlowTransactionGen.genTrans
        s <- FlowTransactionGen.genState.suchThat(_ != FlowTransactionStateStarted)
      } yield (t,s)

      forAll (tGen) { case (t,s) =>
        val event : FlowTransactionEvent = s match {
          case FlowTransactionStateStarted => fail() // Should not happen with the given generator
          case FlowTransactionStateFailed => FlowTransactionFailed(t.id, t.creationProps, Some("Generated"))
          case FlowTransactionStateCompleted => FlowTransactionCompleted(t.id, t.creationProps)
          case FlowTransactionStateUpdated => FlowTransactionUpdate(t.tid, t.creationProps, WorklistStateStarted, "default")
        }
        val updatedTrans : FlowTransaction = tMgr.updateTransaction(event).get
        assert(updatedTrans.first)

        // Make sure a subsequent update is not marked as first
        val completed : FlowTransaction = tMgr.updateTransaction(FlowTransactionCompleted(t.tid, t.creationProps)).get
        assert(!completed.first)
      }
    }

    "maintain the state across transaction manager restarts" in {

      val ids : mutable.ListBuffer[String] = mutable.ListBuffer.empty

      val tMgr : FlowTransactionManager = createTransactionManager("restart")
      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          ids.append(t.tid)
        }
      }

      val tMgr2 : FlowTransactionManager = createTransactionManager("restart")
      assert(ids.distinct.toList.forall{ id =>
        (new ResultPoller[Option[FlowTransaction]](actorSys, timeout, s"${getClass().getSimpleName()} - maintain state")(() => tMgr2.findTransaction(id)).execute {
          case Some(_) =>
          case _ => fail()
        }).isSuccess
      })
    }

    "allow to remove a transaction by id" in {
      val tMgr : FlowTransactionManager = createTransactionManager("remove")

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>

          tMgr.removeTransaction(t.tid)

          (new ResultPoller[Option[FlowTransaction]](actorSys, timeout, s"${getClass().getSimpleName()} - removeTransaction")(() => tMgr.findTransaction(t.tid)).execute {
            case None =>
            case Some(_) => fail()
          }).isSuccess should be (true)
        }
      }
    }

    "allow to retrieve all transactions currently known" in {
      val transactions : mutable.ListBuffer[String] = mutable.ListBuffer.empty
      val tMgr : FlowTransactionManager = createTransactionManager("retrieve")
      tMgr.clearTransactions()

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          (new ResultPoller[Option[FlowTransaction]](actorSys, timeout, s"${getClass().getSimpleName()} - retrieveAll")(() => tMgr.findTransaction(t.tid)).execute {
            case None => fail()
            case Some(trans) => transactions.append(trans.tid)
          }).isSuccess should be (true)
        }
      }

      Await.result( tMgr.withAll{ t => assert(transactions.contains(t.tid)) ; true }, timeout ) should be (transactions.size)
    }

    "allow to retrieve all transactions im completed state" in {
      val transactions : mutable.ListBuffer[FlowTransaction] = mutable.ListBuffer.empty
      val tMgr : FlowTransactionManager = createTransactionManager("transactions")
      tMgr.clearTransactions()

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          (new ResultPoller[Option[FlowTransaction]](actorSys, timeout, s"${getClass().getSimpleName()} - retrieveCompleted")(() => tMgr.findTransaction(t.tid)).execute {
            case None => fail()
            case Some(trans) => transactions.append(trans)
          }).isSuccess should be (true)
        }
      }

      val toComplete : FlowTransaction = transactions.head
      Await.result( tMgr.withCompleted{ _ => }, 3.seconds ) should be (0)

      tMgr.updateTransaction(FlowTransactionCompleted(toComplete.tid, toComplete.creationProps)) match {
        case Success(t) => t.state should be (FlowTransactionStateCompleted)
        case Failure(t) => fail(t)
      }

      Await.result( tMgr.withCompleted{ _ => }, timeout ) should be (1)
    }

    "allow to clear all transactions from the persistence store" in {
      val tMgr : FlowTransactionManager = createTransactionManager("clear")

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          (new ResultPoller[Option[FlowTransaction]](actorSys, timeout, s"${getClass().getSimpleName()} - clearAll}")(() => tMgr.findTransaction(t.tid)).execute {
            case None => fail()
            case Some(trans) =>
          }).isSuccess should be (true)
        }
      }

      Await.result(tMgr.clearTransactions(), timeout)
      Await.result(tMgr.withAll{_ => true}, timeout) should be (0)
    }

    "cleanup closed and failed transaction after the configured retain interval" in {
      val transactions : mutable.ListBuffer[FlowTransaction] = mutable.ListBuffer.empty

      val cfg : FlowTransactionManagerConfig = FlowTransactionManagerConfig(
        dir = new File(BlendedTestSupport.projectTestOutput, "cleanup"),
        retainCompleted = 10.millis,
        retainFailed = 10.millis,
        retainStale = 1.day
      )

      val tMgr : FlowTransactionManager = createTransactionManager(cfg)
      tMgr.clearTransactions()

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          (new ResultPoller[Option[FlowTransaction]](actorSys, timeout, s"${getClass().getSimpleName()} - cleanup}")(() => tMgr.findTransaction(t.tid)).execute {
            case None => fail()
            case Some(trans) => transactions.append(trans)
          }).isSuccess should be (true)
        }
      }

      Await.result( tMgr.withCompleted{ _ => }, timeout) should be (0)

      val toComplete : FlowTransaction = transactions.head
      tMgr.updateTransaction(FlowTransactionCompleted(toComplete.tid, toComplete.creationProps))
      Await.result( tMgr.withCompleted{ _ => }, timeout) should be (1)

      Thread.sleep(cfg.retainCompleted.toMillis * 2)
      Await.result(tMgr.cleanUp(), 3.seconds)

      Await.result(tMgr.withAll{_ => true}, timeout) should be (transactions.size - 1)
      Await.result( tMgr.withCompleted{ _ => }, timeout) should be (0)
    }
  }
}

@RequiresForkedJVM
class BulkCleanupSpec extends TestKit(ActorSystem("bulk"))
  with LoggingFreeSpecLike
  with Matchers
  with ScalaCheckPropertyChecks
  with FTMFactory {

  private val log : Logger = Logger[BulkCleanupSpec]

  private def openFiles : Option[Long] = {
    val os : OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()
    if(os.isInstanceOf[UnixOperatingSystemMXBean]) {
      Some(os.asInstanceOf[UnixOperatingSystemMXBean].getOpenFileDescriptorCount())
    } else {
      None
    }
  }

  private def updateTest[T](ftm : FlowTransactionManager, event : FlowTransactionEvent)(f : FlowTransaction => T) : T = {
    ftm.updateTransaction(event) match {
      case Success(t) => f(t)
      case Failure(e) => fail(e)
    }
  }

  override def createTransactionManager(cfg: FlowTransactionManagerConfig): FlowTransactionManager = {
    val mgr : FileFlowTransactionManager = new FileFlowTransactionManager(cfg)
    system.actorOf(TransactionManagerCleanupActor.props(mgr, cfg))
    mgr
  }

  "The Transaction manager cleanup should" - {

    "Clean up completed and failed transactions correctly" in {
      val tCount : Int = 20000
      val completeRate : Int = 3
      val openRate : Int = 1000

      val startOpen : Long = openFiles.get
      log.info(s"Open files [$startOpen]")

      val openCount : AtomicInteger = new AtomicInteger(0)

      val cfg : FlowTransactionManagerConfig = FlowTransactionManagerConfig(
        dir = new File(BlendedTestSupport.projectTestOutput, "bulk"),
        retainCompleted = 1.seconds,
        retainFailed = 1.seconds,
        retainStale = 1.day
      )

      val tMgr : FlowTransactionManager = createTransactionManager(cfg)
      tMgr.clearTransactions()

      1.to(tCount).foreach{ i =>
        val env : FlowEnvelope = FlowEnvelope(FlowMessage.noProps)
        updateTest(tMgr, FlowTransaction.startEvent(Some(env))){_ =>}

        if (i % 2000 == 0) {
          val msg : String = openFiles.map(c => s"$i -- $c -- ${c - startOpen}").getOrElse("")
          println(msg)
          log.info(msg)
        }

        if (i % openRate == 0) {
          openCount.incrementAndGet()
        } else {
          if (i % completeRate == 0) {
            updateTest(tMgr, FlowTransactionCompleted(env.id, env.flowMessage.header)){_ =>}
          } else {
            updateTest(tMgr, FlowTransactionFailed(env.id, env.flowMessage.header, None)){_ =>}
          }
        }

      }

      Thread.sleep(cfg.retainCompleted.toMillis * 3)
      Await.result(tMgr.withAll{_ => true}, 3.seconds) should be (openCount.get())

      openFiles.foreach{ l =>
        log.info(s"Open files [$l]")
        assert(l - startOpen <= 20)
      }
    }
  }
}

@RequiresForkedJVM
class FileFlowTransactionManagerSpec extends FlowTransactionManagerSpec
  with LoggingFreeSpecLike
  with Matchers
  with ScalaCheckPropertyChecks
  with FTMFactory {

  private val kit : TestKit = new TestKit(ActorSystem("bulk"))
  override val actorSys : ActorSystem = kit.system

  override def createTransactionManager(cfg: FlowTransactionManagerConfig): FlowTransactionManager =
    new FileFlowTransactionManager(cfg)(actorSys)
}
