package blended.streams.dispatcher.internal

import java.io.File

import blended.container.context.api.ContainerIdentifierService
import blended.jms.bridge.{BridgeProviderConfig, BridgeProviderRegistry}
import blended.jms.utils.{JmsQueue, JmsTopic}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojosrBlendedContainer}
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import org.scalatest.Matchers

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class ResourceTypeRouterConfigSpec extends LoggingFreeSpec
  with SimplePojosrBlendedContainer
  with PojoSrTestHelper
  with Matchers {

  private val log = Logger[ResourceTypeRouterConfigSpec]
  private val baseDir = new File(BlendedTestSupport.projectTestOutput, "config").getAbsolutePath()

  System.setProperty("SIBCountry", "cc")

  "The ResourceTypeRouterConfig should" - {

    "instantiate from HOCON correctly" in {

      withSimpleBlendedContainer(baseDir) { sr =>

        implicit val timeout = 1.second
        val idSvc = waitOnService[ContainerIdentifierService](sr)(None) match {
          case None => fail("Id Service not Available")
          case Some(idSvc) =>


            val bridgeCfg = idSvc.containerContext.getContainerConfig().getConfig("blended.jms.bridge")
            val providerList = bridgeCfg.getConfigList("provider").asScala.map { p =>
              BridgeProviderConfig.create(idSvc, p).get
            }.toList
            val provider = new BridgeProviderRegistry(providerList)

            val amq = ProviderResolver.getProvider(provider, "activemq", "activemq").get
            val sonic75 = ProviderResolver.getProvider(provider, "sonic75", "central").get
            val ccQueue = ProviderResolver.getProvider(provider, "sagum", "cc_queue").get
            val ccTopic = ProviderResolver.getProvider(provider, "sagum", "cc_topic").get

            val dispatcherCfg = idSvc.containerContext.getContainerConfig().getConfig("blended.streams.dispatcher")

            val cfg = ResourceTypeRouterConfig.create(idSvc, provider, dispatcherCfg).get

            cfg.defaultProvider should be (amq)
            cfg.eventProvider should be (sonic75)

            (cfg.eventProvider.vendor, cfg.eventProvider.provider) should be ("sonic75", "central")

            cfg.eventProvider.inbound should be (JmsQueue("bridge.data.in"))
            cfg.applicationLogHeader.size should be (3)

            val sagTest = cfg.resourceTypeConfigs.get("SagTest").get

            sagTest.withCBE should be(false)
            sagTest.inboundConfig should be (empty)
            sagTest.outbound should have size(1)

            sagTest.outbound.foreach { out =>
              out.id should be ("default")
              out.bridgeProvider should be (amq)
              out.bridgeDestination should be (Some(JmsTopic("SagTest")))
            }

            val dataFromPosClient = cfg.resourceTypeConfigs.get("DataFromPosClient").get

            dataFromPosClient.withCBE should be (false)
            dataFromPosClient.inboundConfig should be (defined)
            dataFromPosClient.inboundConfig.foreach { in =>
              in.entry should be (JmsQueue("PosClientFromQ"))
              in.header should be (Map("ResourceType" -> "${header.KPosMsgType}"))
            }

            dataFromPosClient.outbound should have size(1)
            dataFromPosClient.outbound.foreach { out =>
              out.id should be ("default")
              out.bridgeProvider should be (ccQueue)
              out.bridgeDestination should be (Some(JmsQueue("/Qucc/sib/kpos/data/out")))
            }

            val kposData = cfg.resourceTypeConfigs.get("KPosData").get

            kposData.outbound should have size(2)
            val vitra = kposData.outbound.filter(_.id == "VitraCom")

            vitra should have size(1)
            vitra.foreach { out =>
              out.timeToLive should be (14400000)
              out.bridgeDestination should be (Some(JmsQueue("VitracomClientToQueue")))
              out.applicationLogHeader should have size(6)
              assert(out.applicationLogHeader.forall(_.startsWith("kpos")))
            }

        }

      }
    }
  }

}
