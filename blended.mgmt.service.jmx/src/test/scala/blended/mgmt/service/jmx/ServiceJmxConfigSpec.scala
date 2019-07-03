package blended.mgmt.service.jmx

import blended.mgmt.service.jmx.internal.ServiceJmxConfig
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FreeSpec, Matchers}

class ServiceJmxConfigSpec extends FreeSpec
  with Matchers {

  val cfg : Config = ConfigFactory.load("collector.conf")
  val collectorCfg = ServiceJmxConfig(cfg)

  "The Collector Config" - {
    "should instantiate from HOCON" in {

      collectorCfg.interval should be(3)

      collectorCfg.templates should have size 1

      collectorCfg.templates should contain key "jmsQueue"

      val jmsQueueCfg = collectorCfg.templates("jmsQueue")

      jmsQueueCfg.name should be("jmsQueue")
      jmsQueueCfg.domain should be("org.apache.activemq")
      jmsQueueCfg.attributes should be(List("EnqueueCount", "DequeueCount", "QueueSize", "InFlightCount"))
      jmsQueueCfg.query should be(Map("type" -> "Broker", "destinationType" -> "Queue", "brokerName" -> "blended"))

      collectorCfg.services should have size 1
      collectorCfg.services should contain key "SampleIn"

      val svcConfig = collectorCfg.services("SampleIn")

      svcConfig.name should be("SampleIn")
      svcConfig.svcType should be("jmsQueue")
      svcConfig.query should be(Map("destinationName" -> "SampleIn"))
    }
  }
}
