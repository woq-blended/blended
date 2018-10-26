package blended.streams.dispatcher.internal.builder

import java.io.File

import blended.jms.utils.{JmsQueue, JmsTopic}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

class ResourceTypeRouterConfigSpec extends LoggingFreeSpec
  with Matchers
  with DispatcherSpecSupport {

  override def country: String = "cc"
  override def location: String = "09999"
  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "config").getAbsolutePath()
  override def loggerName: String = getClass().getName()

  implicit val bs = new DispatcherBuilderSupport {
    override val prefix: String = "App"
    override val streamLogger: Logger = Logger(loggerName)
  }

  System.setProperty(bs.header("Country"), country)

  private val amqId = providerId("activemq", "activemq")
  private val sonicId = providerId("sonic75", "central")
  private val ccQueueId = providerId("sagum", "cc_queue")

  "The ResourceTypeRouterConfig should" - {

    "resolve the configured bridge providers correctly" in {

      withDispatcherConfig() { ctxt =>
        val cfg = ctxt.cfg

        cfg.defaultProvider.id should be (amqId)
        cfg.eventProvider.id should be (sonicId)
        cfg.eventProvider.inbound should be (JmsQueue("bridge.data.in"))
        cfg.applicationLogHeader.size should be (3)
      }
    }

    "resolve a simple dispatcher element correctly" in {
      withDispatcherConfig() { ctxt =>
        val cfg = ctxt.cfg

        val sagTest = cfg.resourceTypeConfigs.get("SagTest").get

        sagTest.withCBE should be(false)
        sagTest.inboundConfig should be(empty)
        sagTest.outbound should have size (1)

        sagTest.outbound.foreach { out =>
          out.id should be("default")
          out.bridgeProvider.id should be(amqId)
          out.bridgeDestination should be(Some(JmsTopic("SagTest")))
        }
      }
    }

    "evaluate an optional inbound destination correctly" in {

      withDispatcherConfig() { ctxt =>
        val cfg = ctxt.cfg

        val dataFromPosClient = cfg.resourceTypeConfigs.get("DataClient").get

        dataFromPosClient.withCBE should be(false)
        dataFromPosClient.inboundConfig should be(defined)
        dataFromPosClient.inboundConfig.foreach { in =>
          in.entry should be(JmsQueue("ClientFromQ"))
          in.header should be(Map("ResourceType" -> "${{#MsgType}}"))
        }

        dataFromPosClient.outbound should have size (1)
        dataFromPosClient.outbound.foreach { out =>
          out.id should be("default")
          out.bridgeProvider.id should be(ccQueueId)
          out.bridgeDestination should be(Some(JmsQueue("/Qucc/data/out")))
        }
      }
    }

    "evaluate multiple outbound configs destination correctly" in {

      withDispatcherConfig() { ctxt =>
        val cfg = ctxt.cfg

        val fanout = cfg.resourceTypeConfigs.get("FanOut").get

        fanout.outbound should have size(2)
        val other = fanout.outbound.filter(_.id == "OtherApp")

        other should have size(1)
        other.foreach { out =>
          out.timeToLive should be (14400000)
          out.bridgeDestination should be (Some(JmsQueue("OtherAppToQueue")))
          out.applicationLogHeader should have size(2)
          out.applicationLogHeader should contain ("keymetric1")
          out.applicationLogHeader should contain ("keymetric3")
        }
      }
    }
  }

}
