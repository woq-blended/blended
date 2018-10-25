package blended.streams.transaction

import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.worklist.WorklistState
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

import scala.util.Try

class FlowTransactionSpec extends LoggingFreeSpec
  with Matchers {

  private val branchHeader = "branch"

  val main = FlowEnvelope(FlowMessage.noProps)

  // create a sample transaction witn n started branches
  private def sampleTransAction(branchCount : Int) : Try[FlowTransaction] = Try {

    val branches = 1.to(branchCount).map(i => main.withHeader(branchHeader, s"$i").get)
    val event = FlowTransaction.startEvent(Some(main))
    val t = FlowTransaction(
      id = event.transactionId,
      creationProps = event.creationProperties
    )

    t.updateTransaction(FlowTransactionUpdate(t.tid, WorklistState.Started, branches:_*), branchHeader).get
  }

  "A FlowTransaction should" - {

    "have an empty worklist after being created" in {

      val t = FlowTransaction(None)
      t.worklist should be (empty)
      t.state should be (FlowTransactionState.Started)
    }

    "reflect the envelope id as transaction id if created with an envelope" in {

      FlowTransaction(Some(main)).tid should be (main.id)
    }

    "a started transaction with n started branches should be in Started state" in {
      val t = sampleTransAction(10).get
      t.state should be (FlowTransactionState.Started)
      t.worklist should have size(10)
    }

    "a started transaction with c/n branches completed should be in Updated state for c < n" in {
      val t = sampleTransAction(10).get

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, WorklistState.Completed, main.withHeader(branchHeader, "5").get), branchHeader
      ).get

      u.state should be (FlowTransactionState.Updated)
      u.worklist should have size(10)
    }

    "a started transaction with all branches completed should be in completed state" in {
      val t = sampleTransAction(10).get

      val branches = 1.to(t.worklist.size).map(i => main.withHeader(branchHeader, s"$i").get)

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, WorklistState.Completed, branches:_*), branchHeader
      ).get

      u.state should be (FlowTransactionState.Completed)
      u.worklist should have size(10)
    }

    "a started transaction with one branch having failed should be in failed state" in {
      val t = sampleTransAction(10).get

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, WorklistState.Failed, main.withHeader(branchHeader, "5").get), branchHeader
      ).get

      u.state should be (FlowTransactionState.Failed)
      u.worklist should have size(10)
    }

    "a started transaction with one branch having timed out should be in failed state" in {
      val t = sampleTransAction(10).get

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, WorklistState.TimeOut, main.withHeader(branchHeader, "5").get), branchHeader
      ).get

      u.state should be (FlowTransactionState.Failed)
      u.worklist should have size(10)
    }

    "complete upon an update with complete regardless of the current worklist" in {

      val env = FlowEnvelope(FlowMessage.noProps)
      val t = FlowTransaction(Some(env))

      val u = t.updateTransaction(FlowTransactionCompleted(t.tid), branchHeader).get

      u.state should be (FlowTransactionState.Completed)
      u.worklist should be (empty)

    }
  }

}
