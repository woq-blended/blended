package blended.streams.transaction

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.{TestKit, TestProbe}
import akka.util.Timeout
import blended.streams.message.{FlowEnvelope, FlowMessage, MsgProperty}
import blended.streams.transaction.internal.FlowTransactionManager.RestartTransactionActor
import blended.streams.transaction.internal.{FlowTransactionActor, FlowTransactionManager}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class FlowTransactionManagerSpec extends TestKit(ActorSystem("transaction"))
  with LoggingFreeSpecLike
  with Matchers {

  private val log = Logger[FlowTransactionManagerSpec]
  def transaction(mgr : ActorRef, id : String)(implicit timeout: Timeout) : Future[FlowTransaction] = {
    log.debug(s"Getting transaction state [$id] from [${mgr.path}]")
    (mgr ? FlowTransactionActor.State(id)).mapTo[FlowTransaction]
  }

  "The transaction manager should" - {

    "create a new transaction for a Transaction Started event" in {

      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[FlowTransaction])
      val mgr = system.actorOf(FlowTransactionManager.props())

      val env = FlowEnvelope(FlowMessage.noProps).withHeader("foo", "bar").get
      mgr.tell(FlowTransaction.startEvent(Some(env)), probe.ref)
      val t = probe.expectMsgType[FlowTransaction]

      t.tid should be (env.id)
      t.creationProps.get("foo") should be (Some(MsgProperty("bar")))

      system.stop(mgr)
    }

    "maintain the state across actor restarts" in {

      implicit val timeout = Timeout(3.seconds)
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[FlowTransaction])
      val mgr = system.actorOf(FlowTransactionManager.props())

      val env = FlowEnvelope(FlowMessage.noProps).withHeader("foo", "bar").get
      mgr.tell(FlowTransaction.startEvent(Some(env)), probe.ref)
      val t = probe.expectMsgType[FlowTransaction]

      t.tid should be (env.id)
      t.creationProps.get("foo") should be (Some(MsgProperty("bar")))

      mgr ! RestartTransactionActor(t.tid)
      val t2 = Await.result(transaction(mgr, t.tid), 3.seconds)

      t2.tid should be (env.id)
      t2.creationProps.get("foo") should be (Some(MsgProperty("bar")))

      system.stop(mgr)
    }

    "maintain the state across transaction manager restarts" in {

      implicit val timeout = Timeout(3.seconds)
      val probe = TestProbe()
      system.eventStream.subscribe(probe.ref, classOf[FlowTransaction])
      val mgr = system.actorOf(FlowTransactionManager.props())

      val env = FlowEnvelope(FlowMessage.noProps).withHeader("foo", "bar").get
      mgr.tell(FlowTransaction.startEvent(Some(env)), probe.ref)
      val t = probe.expectMsgType[FlowTransaction]

      t.tid should be (env.id)
      t.creationProps.get("foo") should be (Some(MsgProperty("bar")))

      system.stop(mgr)

      val mgr2 = system.actorOf(FlowTransactionManager.props())
      val t2 = Await.result(transaction(mgr2, t.tid), 3.seconds)

      t2.tid should be (env.id)
      t2.creationProps.get("foo") should be (Some(MsgProperty("bar")))

      system.stop(mgr2)
    }
  }
}
