package blended.jms.bridge.internal

import java.io.File

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.KillSwitch
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.BlendedStreamsConfig
import blended.streams.message.FlowEnvelope
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}

import scala.concurrent.duration._

@RequiresForkedJVM
class TransactionSendFailedRejectBridgeSpec extends BridgeSpecSupport {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "withoutRetries").getAbsolutePath()

  private def sendOutbound(
    cf: IdAwareConnectionFactory,
    timeout: FiniteDuration,
    msgCount: Int,
    track: Boolean
  ): KillSwitch = {
    val msgs: Seq[FlowEnvelope] = generateMessages(msgCount) { env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut")
        .get
        .withHeader(headerCfg.headerTrack, track)
        .get
    }.get

    sendMessages("bridge.data.out.activemq.external", cf, timeout)(msgs: _*)
  }

  override protected def bridgeActivator: BridgeActivator =
    new BridgeActivator() {
      override protected def streamBuilderFactory(system: ActorSystem)(
        cfg: BridgeStreamConfig,
        streamsCfg: BlendedStreamsConfig
      ): BridgeStreamBuilder =
        new BridgeStreamBuilder(cfg, streamsCfg)(system) {
          override protected def sendTransaction: Flow[FlowEnvelope, FlowEnvelope, NotUsed] =
            Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
              env.withException(new Exception("Boom !"))
            }
        }
    }

  "The outbound bridge should " - {

    "reject envelopes if the send of the transaction event fails and retry is disabled" in logException {
      val timeout: FiniteDuration = 1.second
      val msgCount = 2

      val actorSys: ActorSystem = system(registry)
      val (internal, _) = getConnectionFactories(registry)

      val switch: KillSwitch = sendOutbound(internal, timeout, msgCount, track = true)

      val retried: List[FlowEnvelope] =
        consumeMessages(cf = internal, destName = "retries", timeout = timeout)(actorSys).get
      retried should be(empty)

      consumeEvents(internal, timeout)(actorSys).get should be(empty)
      registry.getBundleContext().getBundle(bundles.size + 1).stop()
      Thread.sleep(5000)

      consumeMessages(
        cf = internal,
        destName = "bridge.data.out.activemq.external",
        expected = msgCount,
        timeout = timeout
      )(actorSys).get should have size msgCount

      switch.shutdown()
    }
  }
}
