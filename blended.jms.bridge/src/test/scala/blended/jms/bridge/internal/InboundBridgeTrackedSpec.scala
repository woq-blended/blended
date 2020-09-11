package blended.jms.bridge.internal

import akka.actor.ActorSystem
import akka.stream.KillSwitch
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionStarted}
import blended.testsupport.RequiresForkedJVM

import scala.concurrent.duration._

@RequiresForkedJVM
class InboundBridgeTrackedSpec extends BridgeSpecSupport {

  private def sendInbound(cf : IdAwareConnectionFactory, timeout : FiniteDuration, msgCount : Int) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut").get
    }.get


    sendMessages("sampleIn", cf, timeout)(msgs:_*)
  }

  "The Inbound Bridge should" - {

    // We only test for tracked transactions as all bridge inbound streams generate transaction started events by design
    "process normal inbound messages with tracked transactions" in logException {
      val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val actorSys : ActorSystem = system(registry)

      val (internal, external) = getConnectionFactories(registry)

      val switch = sendInbound(external, timeout, msgCount)

      val messages : List[FlowEnvelope] =
        consumeMessages(
          cf = internal,
          destName = "bridge.data.in.activemq.external",
          timeout = timeout
        )(actorSys).get

      messages should have size(msgCount)

      messages.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

      val envelopes : List[FlowTransactionEvent] = consumeEvents(internal, timeout)(actorSys).get

      envelopes should have size(msgCount)
      assert(envelopes.forall(_.isInstanceOf[FlowTransactionStarted]))

      switch.shutdown()
    }

    "process messages with optional header configs" in logException {

      val (internal, external) = getConnectionFactories(registry)
      val actorSys : ActorSystem = system(registry)

      val desc = "TestDesc"

      val env : FlowEnvelope = FlowEnvelope(FlowMessage("Header")(FlowMessage.props(
        destHeader(headerCfg.prefix) -> "SampleHeaderOut",
        "Description" -> desc,
        headerCfg.headerTrack -> false
      ).get))

      val switch : KillSwitch = sendMessages("SampleHeaderIn", external, timeout)(env)

      val result : List[FlowEnvelope] = consumeMessages(
        cf = internal,
        destName = "bridge.data.in.activemq.external",
        expected = 1,
        timeout = timeout
      )(actorSys).get

      result should have size 1
      result.head.header[String]("ResourceType") should be (Some(desc))

      switch.shutdown()
    }
  }
}
