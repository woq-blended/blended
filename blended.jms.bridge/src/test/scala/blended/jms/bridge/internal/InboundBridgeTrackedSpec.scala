package blended.jms.bridge.internal

import akka.stream.KillSwitch
import blended.jms.utils.JmsQueue
import blended.streams.jms.JmsProducerSettings
import blended.streams.message.{FlowEnvelope, FlowMessage}
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionStarted}
import blended.testsupport.RequiresForkedJVM

import scala.concurrent.duration._

@RequiresForkedJVM
class InboundBridgeTrackedSpec extends BridgeSpecSupport {

  private def sendInbound(msgCount : Int) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut").get
    }.get


    sendMessages("sampleIn", external)(msgs:_*)
  }

  "The Inbound Bridge should" - {

    // We only test for tracked transactions as all bridge inbound streams generate transaction started events by design
    "process normal inbound messages with tracked transactions" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendInbound(msgCount)

      val messages : List[FlowEnvelope] =
        consumeMessages(internal, "bridge.data.in.activemq.external")(1.second, system, materializer).get

      messages should have size(msgCount)

      messages.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

      val envelopes : List[FlowTransactionEvent] = consumeEvents().get

      envelopes should have size(msgCount)
      assert(envelopes.forall(_.isInstanceOf[FlowTransactionStarted]))

      switch.shutdown()
    }

    "process messages with optional header configs" in {

      val desc = "TestDesc"

      val env : FlowEnvelope = FlowEnvelope(FlowMessage("Header")(FlowMessage.props(
        destHeader(headerCfg.prefix) -> "SampleHeaderOut",
        "Description" -> desc,
        headerCfg.headerTrack -> false
      ).get))

      val pSettings : JmsProducerSettings = JmsProducerSettings(
        log = envLogger,
        headerCfg = headerCfg,
        connectionFactory = external,
        jmsDestination = Some(JmsQueue("SampleHeaderIn"))
      )

      val switch : KillSwitch = sendMessages(pSettings, envLogger, env).get

      val result : List[FlowEnvelope] = consumeMessages(internal, "bridge.data.in.activemq.external").get

      result should have size 1
      result.head.header[String]("ResourceType") should be (Some(desc))

      switch.shutdown()
    }
  }
}
