package blended.jms.bridge.internal

import akka.stream.KillSwitch
import blended.jms.utils.IdAwareConnectionFactory
import blended.streams.message.FlowEnvelope
import blended.testsupport.RequiresForkedJVM

import scala.concurrent.duration._
import blended.streams.transaction.FlowTransactionEvent
import org.osgi.framework.BundleActivator

@RequiresForkedJVM
class SessionFailRetrySpec extends BridgeSpecSupport {

  override def bundles: Seq[(String, BundleActivator)] = super.bundles ++ Seq()

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

  "The outbound bridge should " - {

    "forward messages to the retry queue in case a session for the outbound jms provider could not be created" in logException {
      val timeout: FiniteDuration = 1.second
      val msgCount = 2

      val actorSys = system(registry)
      val internal = namedJmsConnectionFactory(registry, mustConnect = true, timeout = timeout)(
        vendor = "activemq",
        provider = "internal"
      ).get

      val switch = sendOutbound(internal, timeout, msgCount, track = false)

      val messages: List[FlowEnvelope] =
        consumeMessages(
          cf = internal,
          destName = "sampleOut",
          expected = msgCount,
          timeout = timeout
        )(actorSys).get

      messages should have size (msgCount)

      messages.foreach { env =>
        env.header[Unit]("UnitProperty") should be(Some(()))
      }

      val envelopes: List[FlowTransactionEvent] = consumeEvents(internal, timeout)(actorSys).get
      envelopes should be(empty)
      switch.shutdown()
    }

  }
}
