package blended.jms.bridge.internal

import java.io.File

import akka.stream.KillSwitch
import blended.streams.message.FlowEnvelope
import blended.testsupport.{BlendedTestSupport, RequiresForkedJVM}

import scala.concurrent.duration._

@RequiresForkedJVM
class MapToExternalBridgeSpec extends BridgeSpecSupport {

  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "withoutTracking").getAbsolutePath()

  "The outbound bridge should" - {

    "Forward messages that are meant to an external provider even if send via the internal provider" in {
      val msgCount : Int = 1

      val msgs : Seq[FlowEnvelope] = generateMessages(msgCount){ env =>
        env
          .withHeader(headerCfg.headerBridgeVendor, "activemq").get
          .withHeader(headerCfg.headerBridgeProvider, "external").get
          .withHeader(destHeader(headerCfg.prefix), "sampleOut").get
      }.get

      val switch : KillSwitch = sendMessages("bridge.data.out", internal)(msgs:_*)

      val messages : List[FlowEnvelope] =
        consumeMessages(external, "sampleOut")(3.seconds, system, materializer).get

      messages should have size(msgCount)

      switch.shutdown()
    }
  }
}
