package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.{KillSwitch, Materializer}
import blended.streams.BlendedStreamsConfig
import blended.streams.message.FlowEnvelope
import blended.testsupport.RequiresForkedJVM

import scala.concurrent.duration._

@RequiresForkedJVM
class SendFailedRetryBridgeSpec extends BridgeSpecSupport {

  private def sendOutbound(msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get


    sendMessages("bridge.data.out.activemq.external", internal)(msgs:_*)
  }

  // We override the send flow with a flow simply triggering an exception, so that the
  // exceptional path will be triggered
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

  "The outbound bridge should " - {

    "pass the message to the retry destination and not generate a transaction event if the forwarding of the message fails" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, true)

      val retried : List[FlowEnvelope] = consumeMessages(internal, "retries").get

      retried should have size(msgCount)

      retried.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
        env.header[String](headerCfg.headerRetryDestination) should be (Some("bridge.data.out.activemq.external"))
      }

      consumeEvents().get should be (empty)
      consumeMessages(external, "sampleOut").get should be (empty)

      switch.shutdown()
    }
  }
}