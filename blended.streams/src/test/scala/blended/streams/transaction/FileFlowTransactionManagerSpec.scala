package blended.streams.transaction

import java.io.File
import java.lang.management.{ManagementFactory, OperatingSystemMXBean}
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.testkit.TestKit
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import com.sun.management.UnixOperatingSystemMXBean
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

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
  with PropertyChecks { this : FTMFactory =>

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

          Await.result(tMgr.findTransaction(t.tid), 3.seconds) match {
            case None => fail()
            case Some(t2) => assert(t === t2)
          }
        }
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

        Await.result(tMgr2.findTransaction(id), 3.seconds) match {
          case Some(_) => true
          case _ => false
        }
      })
    }

    "allow to remove a transaction by id" in {
      val tMgr : FlowTransactionManager = createTransactionManager("remove")

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>

          tMgr.removeTransaction(t.tid)

          Await.result(tMgr.findTransaction(t.tid), 3.seconds) match {
            case None =>
            case Some(_) => fail()
          }
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
          Await.result(tMgr.findTransaction(t.tid), 3.seconds) match {
            case None => fail()
            case Some(trans) => transactions.append(trans.tid)
          }
        }
      }

      Await.result( tMgr.withAll{ t => assert(transactions.contains(t.tid)) ; true }, 3.seconds ) should be (transactions.size)
    }

    "allow to retrieve all transactions im completed state" in {
      val transactions : mutable.ListBuffer[FlowTransaction] = mutable.ListBuffer.empty
      val tMgr : FlowTransactionManager = createTransactionManager("transactions")
      tMgr.clearTransactions()

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          Await.result(tMgr.findTransaction(t.tid), 3.seconds) match {
            case None => fail()
            case Some(trans) => transactions.append(trans)
          }
        }
      }

      val toComplete : FlowTransaction = transactions.head
      Await.result( tMgr.withCompleted{ _ => }, 3.seconds ) should be (0)

      tMgr.updateTransaction(FlowTransactionCompleted(toComplete.tid, toComplete.creationProps)) match {
        case Success(t) => t.state should be (FlowTransactionStateCompleted)
        case Failure(t) => fail(t)
      }

      Await.result( tMgr.withCompleted{ _ => }, 3.seconds ) should be (1)
    }

    "allow to clear all transactions from the persistence store" in {
      val tMgr : FlowTransactionManager = createTransactionManager("clear")

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          Await.result(tMgr.findTransaction(t.tid), 3.seconds) match {
            case None => fail()
            case Some(_) =>
          }
        }
      }

      Await.result(tMgr.clearTransactions(), 3.seconds)
      Await.result(tMgr.withAll{_ => true}, 3.seconds) should be (0)
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
          Await.result(tMgr.findTransaction(t.tid), 3.seconds) match {
            case None => fail()
            case Some(trans) => transactions.append(trans)
          }
        }
      }

      Await.result( tMgr.withCompleted{ _ => }, 3.seconds ) should be (0)

      val toComplete : FlowTransaction = transactions.head
      tMgr.updateTransaction(FlowTransactionCompleted(toComplete.tid, toComplete.creationProps))
      Await.result( tMgr.withCompleted{ _ => }, 3.seconds ) should be (1)

      Thread.sleep(cfg.retainCompleted.toMillis * 2)
      Await.result(tMgr.cleanUp(), 3.seconds)

      Await.result(tMgr.withAll{_ => true}, 3.seconds) should be (transactions.size - 1)
      Await.result( tMgr.withCompleted{ _ => }, 3.seconds ) should be (0)
    }
  }
}

@RequiresForkedJVM
class BulkCleanupSpec extends TestKit(ActorSystem("bulk"))
  with LoggingFreeSpecLike
  with Matchers
  with PropertyChecks
  with FTMFactory {

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
      val tCount : Int = 50000
      val completeRate : Int = 3
      val openRate : Int = 1000

      val startOpen : Long = openFiles.get

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

        if (i % 5000 == 0) { println(s"$i -- ${openFiles}") }

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

      Thread.sleep(cfg.retainCompleted.toMillis + 1.second.toMillis)
      Await.result(tMgr.withAll{_ => true}, 3.seconds) should be (openCount.get())

      assert(Math.abs(openFiles.get - startOpen) <= 10)
    }
  }
}

@RequiresForkedJVM
class FileFlowTransactionManagerSpec extends FlowTransactionManagerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PropertyChecks
  with FTMFactory {

  private val kit : TestKit = new TestKit(ActorSystem("bulk"))
  private implicit val system : ActorSystem = kit.system

  override def createTransactionManager(cfg: FlowTransactionManagerConfig): FlowTransactionManager =
    new FileFlowTransactionManager(cfg)
}
