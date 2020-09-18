package blended.jms.bridge.internal

import java.io.File

import akka.stream.KillSwitch
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.message.{BinaryFlowMessage, FlowEnvelope, FlowMessage, TextFlowMessage}
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}

import scala.concurrent.duration._

@RequiresForkedJVM
class InboundBridgeUntrackedSpec extends BridgeSpecSupport {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "withoutTracking").getAbsolutePath()

  private def sendInbound(cf : IdAwareConnectionFactory, timeout : FiniteDuration, msgCount : Int) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destHeader(headerCfg.prefix),s"sampleOut").get
    }.get


    sendMessages("sampleIn", cf, timeout)(msgs:_*)
  }

  "The inbound bridge should" - {

    "process normal inbound messages without tracking" in logException {
      val timeout : FiniteDuration = 10.seconds
      val msgCount = 2
      val actorSys = system(registry)
      val (internal, external) = getConnectionFactories(registry)

      val switch = sendInbound(external, timeout, msgCount)

      val messages : List[FlowEnvelope] =
        consumeMessages(
          cf = internal,
          destName = "bridge.data.in.activemq.external",
          expected = msgCount,
          timeout = timeout
        )(actorSys).get

      messages should have size msgCount

      messages.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

      consumeEvents(internal, timeout)(actorSys).get should be (empty)

      switch.shutdown()
    }

    "process text messages with a null body" in logException {
      val timeout : FiniteDuration = 1.second

      val actorSys = system(registry)
      val (internal, external) = getConnectionFactories(registry)

      val msg : FlowMessage = TextFlowMessage(null, FlowMessage.noProps)
      val msgs : Seq[FlowEnvelope] = Seq(FlowEnvelope(msg))

      val switch : KillSwitch = sendMessages("sampleIn", external, timeout)(msgs:_*)

      val messages : List[FlowEnvelope] =
        consumeMessages(
          cf = internal,
          destName = "bridge.data.in.activemq.external",
          expected = msgs.size,
          timeout = timeout
        )(actorSys).get

      messages should have size msgs.size

      consumeEvents(cf = internal, timeout = timeout)(actorSys).get should be (empty)

      switch.shutdown()
    }

    "process messages with an empty binary body" in logException {
      val timeout : FiniteDuration = 1.second

      val actorSys = system(registry)
      val (internal, external) = getConnectionFactories(registry)

      val msg : FlowMessage = BinaryFlowMessage(Array.empty[Byte], FlowMessage.noProps)
      val msgs : Seq[FlowEnvelope] = Seq(FlowEnvelope(msg))

      val switch : KillSwitch = sendMessages("sampleIn", external, timeout)(msgs:_*)

      val messages : List[FlowEnvelope] =
        consumeMessages(
          cf = internal,
          destName = "bridge.data.in.activemq.external",
          expected = msgs.size,
          timeout = timeout
        )(actorSys).get

      messages should have size msgs.size

      consumeEvents(internal, timeout)(actorSys).get should be (empty)

      switch.shutdown()
    }
  }
}
