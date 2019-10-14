package blended.jms.bridge.internal

import java.io.File

import akka.stream.KillSwitch
import blended.streams.message.{BinaryFlowMessage, FlowEnvelope, FlowMessage, TextFlowMessage}
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}

import scala.concurrent.duration._

@RequiresForkedJVM
class InboundBridgeUntrackedSpec extends BridgeSpecSupport {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "withoutTracking").getAbsolutePath()

  private def sendInbound(msgCount : Int) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut").get
    }.get


    sendMessages("sampleIn", external)(msgs:_*)
  }

  "The inbound bridge should" - {

    "process normal inbound messages without tracking" in {
      val timeout : FiniteDuration = 5.seconds
      val msgCount = 2

      val switch = sendInbound(msgCount)

      val messages : List[FlowEnvelope] =
        consumeMessages(internal, "bridge.data.in.activemq.external", timeout)(system, materializer).get

      messages should have size msgCount

      messages.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

      consumeEvents(timeout).get should be (empty)

      switch.shutdown()
    }

    "process text messages with a null body" in {

      val timeout : FiniteDuration = 1.second

      val msg : FlowMessage = TextFlowMessage(null, FlowMessage.noProps)
      val msgs : Seq[FlowEnvelope] = Seq(FlowEnvelope(msg))

      val switch : KillSwitch = sendMessages("sampleIn", external)(msgs:_*)

      val messages : List[FlowEnvelope] =
        consumeMessages(internal, "bridge.data.in.activemq.external", timeout)(system, materializer).get

      messages should have size msgs.size

      consumeEvents(timeout).get should be (empty)

      switch.shutdown()
    }

    "process messages with an empty binary body" in {
      val timeout : FiniteDuration = 1.second

      val msg : FlowMessage = BinaryFlowMessage(Array.empty[Byte], FlowMessage.noProps)
      val msgs : Seq[FlowEnvelope] = Seq(FlowEnvelope(msg))

      val switch : KillSwitch = sendMessages("sampleIn", external)(msgs:_*)

      val messages : List[FlowEnvelope] =
        consumeMessages(internal, "bridge.data.in.activemq.external", timeout)(system, materializer).get

      messages should have size msgs.size

      consumeEvents(timeout).get should be (empty)

      switch.shutdown()
    }
  }
}
