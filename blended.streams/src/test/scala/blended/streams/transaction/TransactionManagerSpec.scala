package blended.streams.transaction

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestProbe}
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.internal.TransactionManager
import blended.testsupport.scalatest.LoggingFreeSpecLike
import org.scalatest.Matchers

class TransactionManagerSpec extends TestKit(ActorSystem("transaction"))
  with LoggingFreeSpecLike
  with Matchers {

  "The transaction manager should" - {

    "create a new transaction for a Transaction Started event" in {

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[FlowTransaction])
      val mgr = system.actorOf(TransactionManager.props("branch"), "TransactionManager")

      val env = FlowEnvelope(FlowMessage.noProps, "test")
      mgr ! FlowTransaction.startTransaction(Some(env))

      probe.expectMsgType[FlowTransaction]
    }
  }


}
