package blended.streams.dispatcher.internal.builder

import blended.jms.utils.{JmsQueue, JmsTopic}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

class ResourceTypeRouterConfigSpec extends LoggingFreeSpec
  with Matchers
  with DispatcherSpecSupport {

  private val amqId = providerId("activemq", "activemq")
  private val sonicId = providerId("sonic75", "central")
  private val ccQueueId = providerId("sagum", "cc_queue")

  "The ResourceTypeRouterConfig should" - {

    "resolve the configured bridge providers correctly" in {

      withDispatcherConfig { ctxt =>
        val cfg = ctxt.cfg

        cfg.defaultProvider.id should be (amqId)
        cfg.eventProvider.id should be (sonicId)
        cfg.eventProvider.inbound should be (JmsQueue("bridge.data.in"))
        cfg.applicationLogHeader.size should be (3)
      }
    }

    "resolve a simple dispatcher element correctly" in {
      withDispatcherConfig { ctxt =>
        val cfg = ctxt.cfg

        val sagTest = cfg.resourceTypeConfigs.get("SagTest").get

        sagTest.withCBE should be(false)
        sagTest.inboundConfig should be(empty)
        sagTest.outbound should have size (1)

        sagTest.outbound.foreach { out =>
          out.id should be("default")
          out.outboundHeader should have size(1)
          val ohCfg = out.outboundHeader.head
          ohCfg.bridgeProviderConfig.id should be(amqId)
          ohCfg.bridgeDestination should be(Some(JmsTopic("SagTest")))
        }
      }
    }

    "evaluate an optional inbound destination correctly" in {

      withDispatcherConfig { ctxt =>
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
          out.outboundHeader should have size(1)
          val ohCfg = out.outboundHeader.head
          ohCfg.bridgeProviderConfig.id should be(ccQueueId)
          ohCfg.bridgeDestination should be(Some(JmsQueue("/Qucc/data/out")))
        }
      }
    }

    "evaluate multiple outbound configs destination correctly" in {

      withDispatcherConfig { ctxt =>
        val cfg = ctxt.cfg

        val fanout = cfg.resourceTypeConfigs.get("FanOut").get

        fanout.outbound should have size(2)
        val other = fanout.outbound.filter(_.id == "OtherApp")

        other should have size(1)
        other.foreach { out =>
          val ohCfg = other.head.outboundHeader.head
          ohCfg.timeToLive should be (14400000)
          ohCfg.bridgeDestination should be (Some(JmsQueue("OtherAppToQueue")))
          ohCfg.applicationLogHeader should have size(2)
          ohCfg.applicationLogHeader should contain ("keymetric1")
          ohCfg.applicationLogHeader should contain ("keymetric3")
        }
      }
    }
  }

}
