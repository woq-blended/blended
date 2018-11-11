package blended.streams.transaction

import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.worklist.WorklistState
import blended.testsupport.scalatest.LoggingFreeSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers

import scala.collection.JavaConverters._
import FlowTransaction.{envelope2Transaction, transaction2envelope}
import blended.streams.transaction

import scala.util.Try

class FlowTransactionSpec extends LoggingFreeSpec
  with Matchers {

  private val branchHeader = "branch"

  val main = FlowEnvelope(FlowMessage.noProps)

  // create a sample transaction witn n started branches
  private def sampleTransAction(branchCount : Int) : Try[FlowTransaction] = Try {

    val branches : Seq[String] = 1.to(branchCount).map{ i => s"$i"}

    val event = FlowTransaction.startEvent(Some(main))
    val t = FlowTransaction(
      id = event.transactionId,
      creationProps = event.properties
    )

    t.updateTransaction(FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistState.Started, branches:_*)).get
  }

  private val cfg : FlowHeaderConfig = FlowHeaderConfig.create(ConfigFactory.parseMap(
    Map(
      "prefix" -> "App",
      "transactionId" -> "AppFlowTransId",
      "transactionState" -> "AppFlowTransState",
      "branchId" -> "AppFlowBranch"
    ).asJava
  ))

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
        FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistState.Completed, "5")
      ).get

      u.state should be (FlowTransactionState.Updated)
      u.worklist should have size(10)
    }

    "a started transaction with all branches completed should be in completed state" in {
      val t = sampleTransAction(10).get

      val branches = 1.to(t.worklist.size).map(i => s"$i")

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistState.Completed, branches:_*)
      ).get

      u.state should be (FlowTransactionState.Completed)
      u.worklist should have size(10)
    }

    "a started transaction with one branch having failed should be in failed state" in {
      val t = sampleTransAction(10).get

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistState.Failed, "5")
      ).get

      u.state should be (FlowTransactionState.Failed)
      u.worklist should have size(10)
    }

    "a started transaction with one branch having timed out should be in failed state" in {
      val t = sampleTransAction(10).get

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistState.TimeOut, "5")
      ).get

      u.state should be (FlowTransactionState.Failed)
      u.worklist should have size(10)
    }

    "complete upon an update with complete regardless of the current worklist" in {

      val env = FlowEnvelope(FlowMessage.noProps)
      val t = FlowTransaction(Some(env))

      val u = t.updateTransaction(FlowTransactionCompleted(t.tid, FlowMessage.noProps)).get

      u.state should be (FlowTransactionState.Completed)
      u.worklist should be (empty)
    }

    "can be transformed into a FlowEnvelope and vice versa" in {

      def singleTest(t: FlowTransaction): Unit = {
        val envelope = transaction2envelope(cfg)(t)
        val t2 = envelope2Transaction(cfg)(envelope)

        t2.tid should be(t.tid)
        t2.state should be(t.state)
        assert(t.worklist.forall { case (k, v) => t2.worklist.get(k).get == v })
      }

      singleTest(FlowTransaction(Some(FlowEnvelope())))
      singleTest(sampleTransAction(10).get)

      val t = sampleTransAction(10).get
      t.updateTransaction(FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistState.Completed, "5"))
      singleTest(t)

      val t2 = sampleTransAction(10).get
      t.updateTransaction(transaction.FlowTransactionCompleted(t.tid, FlowMessage.noProps))
      singleTest(t2)
    }
  }
}
