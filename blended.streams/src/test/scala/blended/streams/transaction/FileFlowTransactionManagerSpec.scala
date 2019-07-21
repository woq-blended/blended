package blended.streams.transaction

import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import blended.streams.transaction.internal.FileFlowTransactionManager
import blended.testsupport.RequiresForkedJVM
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.util.{Failure, Success}

@RequiresForkedJVM
class FileFlowTransactionManagerSpec extends LoggingFreeSpec
  with Matchers {

  private val log = Logger[FileFlowTransactionManagerSpec]

  private val mgr : FlowTransactionManager  = new FileFlowTransactionManager()

  private def singleTest(event : FlowTransactionEvent)(f : FlowTransaction => Unit) : Unit = {
    mgr.updateTransaction(event) match {
      case Success(t) => f(t)
      case Failure(e) => fail(e)
    }
  }

  "The transaction manager should" - {

    "create a new transaction for a Transaction Started event" in {

      val env = FlowEnvelope(FlowMessage.noProps).withHeader("foo", "bar").get

      singleTest(FlowTransaction.startEvent(Some(env))) { t =>
        t.tid should be (env.id)
        t.creationProps.get("foo") should be (Some(MsgProperty("bar")))
      }
    }

    "maintain the state across transaction manager restarts" in {

      val env = FlowEnvelope(FlowMessage.noProps).withHeader("foo", "bar").get

      singleTest(FlowTransaction.startEvent(Some(env))){ t =>
        t.tid should be (env.id)
        t.creationProps.get("foo") should be (Some(MsgProperty("bar")))
      }
    }
  }

}
