package blended.jms.bridge.internal

import akka.stream.KillSwitch
import blended.streams.message.FlowEnvelope
import blended.streams.transaction.{FlowTransactionEvent, FlowTransactionUpdate}
import blended.testsupport.RequiresForkedJVM

import scala.concurrent.duration._

@RequiresForkedJVM
class OutboundBridgeSpec extends BridgeSpecSupport {

  private def sendOutbound(msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get


    sendMessages("bridge.data.out.activemq.external", internal)(msgs:_*)
  }

  "The outbound bridge should " - {

    "process normal inbound messages with untracked transactions" in {
      val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, track = false)

      val messages : List[FlowEnvelope] =
        consumeMessages(external, "sampleOut", timeout).get

      messages should have size(msgCount)

      messages.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

      val envelopes : List[FlowTransactionEvent] = consumeEvents(timeout).get

      envelopes should be (empty)

      switch.shutdown()
    }

    "process normal inbound messages with tracked transactions" in {
      val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, true)

      val messages : List[FlowEnvelope] =
        consumeMessages(external, "sampleOut", timeout).get

      messages should have size(msgCount)

      messages.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

      val envelopes : List[FlowTransactionEvent] = consumeEvents(timeout).get

      envelopes should have size(msgCount)
      assert(envelopes.forall(_.isInstanceOf[FlowTransactionUpdate]))
    }
  }
}
