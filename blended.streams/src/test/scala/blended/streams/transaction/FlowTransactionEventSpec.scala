package blended.streams.transaction

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers
import blended.streams.message.MsgProperty.Implicits._
import FlowTransactionEvent.{envelope2event, event2envelope}
import blended.streams.worklist.WorklistState
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

class FlowTransactionEventSpec extends LoggingFreeSpec
  with Matchers {

  private val cfg : FlowHeaderConfig = FlowHeaderConfig.create(ConfigFactory.parseMap(
    Map(
      "prefix" -> "App",
      "transactionId" -> "AppFlowTransId",
      "transactionState" -> "AppFlowTransState",
      "branchId" -> "AppFlowBranch"
    ).asJava
  ))

  "A FlowTransactionEvent" - {

    "can be transformed into a FlowEnvelope and vice versa (started)" in {

      val started = FlowTransactionStarted(
        transactionId = "started",
        creationProperties = Map(
          "foo" -> "bar",
          "count" -> 7
        )
      )

      val startedTrans = envelope2event(cfg)(event2envelope(cfg)(started)).get

      startedTrans.transactionId should be (started.transactionId)
      startedTrans.state should be (started.state)
      started.asInstanceOf[FlowTransactionStarted].creationProperties should be (started.creationProperties)
    }

    "can be transformed into a FlowEnvelope and vice versa (completed)" in {

      val completed  = FlowTransactionCompleted("completed")

      val completedTrans = envelope2event(cfg)(event2envelope(cfg)(completed)).get

      completedTrans.transactionId should be (completed.transactionId)
      completedTrans.state should be (completed.state)
    }

    "can be transformed into a FlowEnvelope and vice versa (failed)" in {

      val failed  = FlowTransactionFailed("failed", Some("This did not work"))

      val failedTrans = envelope2event(cfg)(event2envelope(cfg)(failed)).get

      failedTrans.transactionId should be (failed.transactionId)
      failedTrans.state should be (failed.state)
      failedTrans.asInstanceOf[FlowTransactionFailed].reason should be (failed.reason)
    }

    "can be transformed into a FlowEnvelope and vice versa (update)" in {

      def singleTest(updated: FlowTransactionUpdate) : Unit = {
        val updatedTrans = envelope2event(cfg)(event2envelope(cfg)(updated)).get

        updatedTrans.transactionId should be (updated.transactionId)
        updatedTrans.state should be (updated.state)
        updatedTrans.asInstanceOf[FlowTransactionUpdate].updatedState should be (updated.updatedState)
        updatedTrans.asInstanceOf[FlowTransactionUpdate].branchIds should be (updated.branchIds)
      }

      singleTest(FlowTransactionUpdate("updated", WorklistState.Started, "branch-1", "branch-2"))
      singleTest(FlowTransactionUpdate("updated1", WorklistState.Failed))
      singleTest(FlowTransactionUpdate("updated1", WorklistState.TimeOut))
      singleTest(FlowTransactionUpdate("updated2", WorklistState.Completed, "branch-3"))
    }
  }

}
