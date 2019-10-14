package blended.jms.bridge.internal

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.{KillSwitch, Materializer}
import blended.streams.BlendedStreamsConfig
import blended.streams.message.FlowEnvelope
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}

import scala.concurrent.duration._

@RequiresForkedJVM
class TransactionSendFailedRejectBridgeSpec extends BridgeSpecSupport {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "withoutRetries").getAbsolutePath()

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
      cfg: BridgeStreamConfig, streamsCfg : BlendedStreamsConfig
    ): BridgeStreamBuilder =
      new BridgeStreamBuilder(cfg, streamsCfg)(system, materializer) {
        override protected def sendTransaction : Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
          Flow.fromFunction[FlowEnvelope, FlowEnvelope]{ env =>
            env.withException(new Exception("Boom !"))
          }
      }
  }

  "The outbound bridge should " - {

    "reject envelopes if the send of the transaction event fails and retry is disabled" in {
      val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val switch = sendOutbound(msgCount, true)

      val retried : List[FlowEnvelope] = consumeMessages(internal, "retries", timeout).get
      retried should be (empty)

      consumeEvents(timeout).get should be (empty)

      consumeMessages(internal, "bridge.data.out.activemq.external", timeout).get should have size(2)

      switch.shutdown()
    }
  }
}
