package blended.streams.transaction

import java.io.File

import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

import scala.concurrent.duration._
import scala.collection.mutable
import scala.util.{Failure, Success}

trait FTMFactory {
  def createTransactionManager(dir : String) : FlowTransactionManager =
    createTransactionManager(FlowTransactionManagerConfig(new File(BlendedTestSupport.projectTestOutput, dir)))

  def createTransactionManager(cfg : FlowTransactionManagerConfig) : FlowTransactionManager
}

trait FlowTransactionManagerSpec extends LoggingFreeSpec
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

          tMgr.findTransaction(t.tid).get match {
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
        tMgr2.findTransaction(id) match {
          case Success(Some(_)) => true
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

          tMgr.findTransaction(t.tid).get match {
            case None =>
            case Some(_) => fail()
          }
        }
      }
    }

    "allow to retrieve all transactions currently known" in {
      val transactions : mutable.ListBuffer[FlowTransaction] = mutable.ListBuffer.empty
      val tMgr : FlowTransactionManager = createTransactionManager("retrieve")
      tMgr.clearTransactions()

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          tMgr.findTransaction(t.tid).get match {
            case None => fail()
            case Some(trans) => transactions.append(trans)
          }
        }
      }

      val known : List[FlowTransaction] = tMgr.transactions.toList
      known.size should be (transactions.size)
      assert(known.forall{transactions.contains})
    }

    "allow to retrieve all transactions im completed state" in {
      val transactions : mutable.ListBuffer[FlowTransaction] = mutable.ListBuffer.empty
      val tMgr : FlowTransactionManager = createTransactionManager("transactions")
      tMgr.clearTransactions()

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          tMgr.findTransaction(t.tid).get match {
            case None => fail()
            case Some(trans) => transactions.append(trans)
          }
        }
      }

      tMgr.completed.toList should be (empty)

      val toComplete : FlowTransaction = transactions.head
      tMgr.updateTransaction(FlowTransactionCompleted(toComplete.tid, toComplete.creationProps))

      tMgr.completed.toList.map(_.tid) should be (List(toComplete.tid))
    }

    "allow to clear all transactions from the persistence store" in {
      val tMgr : FlowTransactionManager = createTransactionManager("clear")

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        updateTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          tMgr.findTransaction(t.tid).get match {
            case None => fail()
            case Some(_) =>
          }
        }
      }

      tMgr.clearTransactions()
      tMgr.transactions should be (empty)
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
          tMgr.findTransaction(t.tid).get match {
            case None => fail()
            case Some(trans) => transactions.append(trans)
          }
        }
      }

      tMgr.completed.toList should be (empty)

      val toComplete : FlowTransaction = transactions.head
      tMgr.updateTransaction(FlowTransactionCompleted(toComplete.tid, toComplete.creationProps))
      tMgr.completed.toList.map(_.tid) should be (List(toComplete.tid))

      Thread.sleep(cfg.retainCompleted.toMillis + 10)
      tMgr.cleanUp()

      tMgr.transactions.toList should have size (transactions.size - 1)
      tMgr.completed.toList.map(_.tid) should be (empty)
    }

    "handle bulk cleanups" in {
      val tCount : Int = 50000

      val cfg : FlowTransactionManagerConfig = FlowTransactionManagerConfig(
        dir = new File(BlendedTestSupport.projectTestOutput, "bulk"),
        retainCompleted = 10.millis,
        retainFailed = 10.millis,
        retainStale = 1.day
      )

      val tMgr : FlowTransactionManager = createTransactionManager(cfg)
      tMgr.clearTransactions()

      1.to(tCount).foreach{ _ =>
        val env : FlowEnvelope = FlowEnvelope(FlowMessage.noProps)
        updateTest(tMgr, FlowTransaction.startEvent(Some(env))){_ =>}
      }

      tMgr.transactions.toList should have size tCount
      val first : FlowTransaction = tMgr.transactions.take(1).toList.head
      updateTest(tMgr, FlowTransactionCompleted(first.tid, first.creationProps)){_ =>}

      Thread.sleep(cfg.retainCompleted.toMillis + 10)
      tMgr.cleanUp()

      tMgr.transactions.size should be (tCount - 1)
    }
  }
}

@RequiresForkedJVM
class FileFlowTransactionManagerSpec extends FlowTransactionManagerSpec with FTMFactory {
  override def createTransactionManager(cfg: FlowTransactionManagerConfig): FlowTransactionManager =
    new FileFlowTransactionManager(cfg)
}
