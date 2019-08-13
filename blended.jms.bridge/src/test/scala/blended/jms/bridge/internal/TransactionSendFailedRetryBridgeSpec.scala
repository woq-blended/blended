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
class TransactionSendFailedRetryBridgeSpec extends BridgeSpecSupport {

  private def sendOutbound(msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get


    sendMessages("bridge.data.out.activemq.external", internal)(msgs:_*)
  }

  override protected def bridgeActivator: BridgeActivator = new BridgeActivator() {
    override protected def streamBuilderFactory(system: ActorSystem)(materializer: Materializer)(
      cfg: BridgeStreamConfig, streamsCfg: BlendedStreamsConfig
    ): BridgeStreamBuilder =
      new BridgeStreamBuilder(cfg, streamsCfg)(system, materializer) {

        override protected def sendTransaction : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
          Flow.fromFunction[FlowEnvelope, FlowEnvelope]{ env =>
            env.withException(new Exception("Boom !"))
          }
      }
  }

  "The outbound bridge should " - {

    "pass messages to the retry destination if the send of the transaction envelope fails" in {
      implicit val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, true)

      val retried : List[FlowEnvelope] = consumeMessages(internal, "retries").get
      retried should have size (msgCount)

      consumeEvents().get should be (empty)

      retried.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

      consumeMessages(internal, "bridge.data.out.activemq.external").get should be (empty)

      switch.shutdown()
    }
  }
}

