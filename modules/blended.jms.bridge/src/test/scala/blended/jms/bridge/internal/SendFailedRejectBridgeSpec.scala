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
class SendFailedRejectBridgeSpec extends BridgeSpecSupport {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "withoutRetries").getAbsolutePath()

  private def sendOutbound(cf : IdAwareConnectionFactory, msgCount : Int, track : Boolean) : KillSwitch = {
    val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
      env
        .withHeader(destHeader(headerCfg.prefix), s"sampleOut").get
        .withHeader(headerCfg.headerTrack, track).get
    }.get


    sendMessages("bridge.data.out.activemq.external", cf)(msgs:_*)
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

    "reject the messages in a forward fails and no retry destination is defined" in logException {
      val timeout : FiniteDuration = 1.second
      val msgCount = 2

      val actorSys = system(registry)
      val (internal, external) = getConnectionFactories(registry)

      val switch = sendOutbound(internal, msgCount, track = true)

      val retried : List[FlowEnvelope] = consumeMessages(
        cf = internal,
        destName = "retries",
        timeout = timeout
      )(actorSys).get
      retried should be (empty)

      consumeEvents(internal, timeout)(actorSys).get should be (empty)

      retried.foreach{ env =>
        env.header[Unit]("UnitProperty") should be (Some(()))
      }

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
