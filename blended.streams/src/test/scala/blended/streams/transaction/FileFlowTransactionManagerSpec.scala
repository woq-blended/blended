package blended.streams.transaction

import java.io.File

import blended.streams.message.FlowEnvelope
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

import scala.collection.mutable
import scala.util.{Failure, Success}

@RequiresForkedJVM
class FileFlowTransactionManagerSpec extends LoggingFreeSpec
  with Matchers
  with PropertyChecks {

  private def mgr : FlowTransactionManager  =
    FileFlowTransactionManager(new File(BlendedTestSupport.projectTestOutput, "transactions"))

  private def singleTest[T](ftm : FlowTransactionManager, event : FlowTransactionEvent)(f : FlowTransaction => T) : T = {
    ftm.updateTransaction(event) match {
      case Success(t) => f(t)
      case Failure(e) => fail(e)
    }
  }

  "The transaction manager should" - {

    "create a new transaction for a Transaction Started event" in {

      val tMgr : FlowTransactionManager = mgr

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        singleTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>

          tMgr.findTransaction(t.tid).get match {
            case None => fail()
            case Some(t2) => assert(t === t2)
          }
        }
      }
    }

    "maintain the state across transaction manager restarts" in {

      val ids : mutable.ListBuffer[String] = mutable.ListBuffer.empty

      val tMgr : FlowTransactionManager = mgr
      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        singleTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>
          ids.append(t.tid)
        }
      }

      val tMgr2 : FlowTransactionManager = mgr
      assert(ids.distinct.toList.forall{ id =>
        tMgr2.findTransaction(id) match {
          case Success(Some(_)) => true
          case _ => false
        }
      })
    }

    "allow to remove a transaction by id" in {
      val tMgr : FlowTransactionManager = mgr

      forAll(FlowTransactionGen.genTrans) { t =>
        val env : FlowEnvelope = FlowEnvelope(t.creationProps)

        singleTest(tMgr, FlowTransaction.startEvent(Some(env))) { t =>

          tMgr.removeTransaction(t.tid)

          tMgr.findTransaction(t.tid).get match {
            case None =>
            case Some(_) => fail()
          }
        }
      }
    }
  }

}
