package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Flow
import blended.streams.BlendedStreamsConfig
import blended.streams.message.FlowEnvelope
import blended.testsupport.RequiresForkedJVM

import scala.concurrent.duration._

@RequiresForkedJVM
class InboundRejectBridgeSpec extends BridgeSpecSupport {

  private def sendInbound(msgCount : Int) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut").get
    }.get


    sendMessages("sampleIn", external)(msgs:_*)
  }

  override protected def bridgeActivator: BridgeActivator = new BridgeActivator() {
    override protected def streamBuilderFactory(system: ActorSystem)(materializer: Materializer)(
      cfg: BridgeStreamConfig, streamsCfg : BlendedStreamsConfig
    ): BridgeStreamBuilder =
      new BridgeStreamBuilder(cfg, streamsCfg)(system, materializer) {
        override protected def jmsSend: Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
          env.withException(new Exception("Boom"))
        }
      }
  }

  "The inbound bridge should" - {

    "reject messages in case the send forward fails" in {
      val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendInbound(msgCount)

      consumeMessages(internal, "bridge.data.in.activemq.external", timeout)(system, materializer).get should be (empty)
      consumeEvents(timeout).get should be (empty)

      consumeMessages(external, "sampleIn", timeout).get should have size(msgCount)

      switch.shutdown()
    }
  }
}
