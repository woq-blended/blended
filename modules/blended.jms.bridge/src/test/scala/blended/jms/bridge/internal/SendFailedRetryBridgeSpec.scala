package blended.jms.bridge.internal

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.Flow
import akka.stream.KillSwitch
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.BlendedStreamsConfig
import blended.streams.message.FlowEnvelope
import blended.testsupport.RequiresForkedJVM
import scala.concurrent.duration.FiniteDuration

@RequiresForkedJVM
class SendFailedRetryBridgeSpec extends BridgeSpecSupport {

  private def sendOutbound(cf : IdAwareConnectionFactory, timeout : FiniteDuration, msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get


    sendMessages("bridge.data.out.activemq.external", cf, timeout)(msgs:_*)
  }

  // We override the send flow with a flow simply triggering an exception, so that the
  // exceptional path will be triggered
  override protected def bridgeActivator: BridgeActivator = new BridgeActivator() {

    override protected def streamBuilderFactory(system: ActorSystem)(
      cfg: BridgeStreamConfig, streamsCfg : BlendedStreamsConfig
    ): BridgeStreamBuilder =
      new BridgeStreamBuilder(cfg, streamsCfg)(system) {
        override protected def jmsSend: Flow[FlowEnvelope, FlowEnvelope, NotUsed] = Flow.fromFunction[FlowEnvelope, FlowEnvelope] { env =>
          env.withException(new Exception("Boom"))
        }
      }
  }

  "The outbound bridge should " - {

    "pass the message to the retry destination and not generate a transaction event if the forwarding of the message fails" in logException {
      val msgCount = 2

      val actorSys = system(registry)
      val (internal, external) = getConnectionFactories(registry)

      val switch = sendOutbound(internal, timeout, msgCount, track = true)

      val retried : List[FlowEnvelope] = consumeMessages(
        cf = internal,
        destName = "retries",
        expected = msgCount,
        timeout = timeout
      )(actorSys).get

      retried should have size msgCount

      retried.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
        env.header[String](headerCfg.headerRetryDestination) should be (Some("bridge.data.out.activemq.external"))
      }

      consumeEvents(internal, timeout)(actorSys).get should be (empty)
      consumeMessages(cf = external, destName = "sampleOut", timeout = timeout)(actorSys).get should be (empty)

      switch.shutdown()
    }
  }
}
