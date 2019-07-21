package blended.streams.transaction

import java.util.Date

import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction
import blended.streams.transaction.FlowTransaction.{envelope2Transaction, transaction2envelope}
import blended.streams.worklist._
import blended.testsupport.scalatest.LoggingFreeSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers

import scala.collection.JavaConverters._
import scala.util.Try

class FlowTransactionSpec extends LoggingFreeSpec
  with Matchers {

  private val branchCount = 10

  val main = FlowEnvelope(FlowMessage.noProps)

  // create a sample transaction witn n started branches
  private def sampleTransAction(branchCount : Int, state: WorklistState = WorklistStateStarted) : Try[FlowTransaction] = Try {

    val branches : Seq[String] = 1.to(branchCount).map{ i => s"$i"}

    val event = FlowTransaction.startEvent(Some(main))

    val now : Date = new Date()
    val t = FlowTransaction(
      created = now,
      lastUpdate = now,
      id = event.transactionId,
      creationProps = event.properties
    )

    t.updateTransaction(FlowTransactionUpdate(t.tid, FlowMessage.noProps, state, branches:_*)).get
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
      t.state should be (FlowTransactionStateStarted)
    }

    "reflect the envelope id as transaction id if created with an envelope" in {
      FlowTransaction(Some(main)).tid should be (main.id)
    }

    "a started transaction with n started branches should be in Started state" in {
      val t = sampleTransAction(branchCount).get
      t.state should be (FlowTransactionStateStarted)
      t.worklist should have size branchCount
    }

    "a started transaction with c/n branches completed should be in Updated state for c < n" in {
      val t = sampleTransAction(branchCount).get

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistStateCompleted, "5")
      ).get

      u.state should be (FlowTransactionStateUpdated)
      u.worklist should have size branchCount
    }

    "a branch within a started transaction requires a started AND a completion update to complete" in {
      val t = sampleTransAction(1).get
      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistStateCompleted, "1")
      ).get

      u.state should be (FlowTransactionStateCompleted)
      u.worklist should have size 1

      val t2 = sampleTransAction(1, WorklistStateCompleted).get
      t2.state should be (FlowTransactionStateUpdated)

      val u2 = t2.updateTransaction(
        FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistStateStarted, "1")
      ).get

      u2.state should be (FlowTransactionStateCompleted)
      u2.worklist should have size 1
    }

    "a started transaction with all branches completed should be in completed state" in {
      val t = sampleTransAction(branchCount).get

      val branches = 1.to(t.worklist.size).map(i => s"$i")

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistStateCompleted, branches:_*)
      ).get

      u.state should be (FlowTransactionStateCompleted)
      u.worklist should have size branchCount
    }

    "a started transaction with one branch having failed should be in failed state" in {
      val t = sampleTransAction(branchCount).get

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistStateFailed, "5")
      ).get

      u.state should be (FlowTransactionStateFailed)
      u.worklist should have size 10
    }

    "a started transaction with one branch having timed out should be in failed state" in {
      val t = sampleTransAction(branchCount).get

      val u = t.updateTransaction(
        FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistStateTimeout, "5")
      ).get

      u.state should be (FlowTransactionStateFailed)
      u.worklist should have size 10
    }

    "complete upon an update with complete regardless of the current worklist" in {

      val env = FlowEnvelope(FlowMessage.noProps)
      val t = FlowTransaction(Some(env))

      val u = t.updateTransaction(FlowTransactionCompleted(t.tid, FlowMessage.noProps)).get

      u.state should be (FlowTransactionStateCompleted)
      u.worklist should be (empty)
    }

    "can be transformed into a FlowEnvelope and vice versa" in {

      def singleTest(t: FlowTransaction): Unit = {
        val envelope = transaction2envelope(cfg)(t)
        val t2 = envelope2Transaction(cfg)(envelope)

        t2.tid should be(t.tid)
        t2.state should be(t.state)
        t2.created should be(t.created)
        t2.lastUpdate should be(t.lastUpdate)
        assert(t.worklist.forall { case (k, v) => t2.worklist(k) == v })
      }

      singleTest(FlowTransaction(Some(FlowEnvelope())))
      singleTest(sampleTransAction(branchCount).get)

      val t = sampleTransAction(branchCount).get
      t.updateTransaction(FlowTransactionUpdate(t.tid, FlowMessage.noProps, WorklistStateCompleted, "5"))
      singleTest(t)

      val t2 = sampleTransAction(branchCount).get
      t.updateTransaction(transaction.FlowTransactionCompleted(t.tid, FlowMessage.noProps))
      singleTest(t2)
    }
  }
}
