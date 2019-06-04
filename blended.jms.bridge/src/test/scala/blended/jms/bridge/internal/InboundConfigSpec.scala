package blended.jms.bridge.internal

import java.io.File

import blended.container.context.impl.internal.ContainerIdentifierServiceImpl
import blended.jms.utils.{JmsDurableTopic, JmsQueue}
import blended.streams.processor.HeaderProcessorConfig
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.MockContainerContext
import blended.testsupport.scalatest.LoggingFreeSpec
import com.typesafe.config.ConfigFactory
import org.scalatest.Matchers

class InboundConfigSpec extends LoggingFreeSpec
  with Matchers {

  "The inbound config should" - {

    System.setProperty("BlendedCountry", "de")
    System.setProperty("BlendedLocation", "09999")

    val baseDir = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()
    val idSvc = new ContainerIdentifierServiceImpl(new MockContainerContext(baseDir))

    "initialize from a plain config correctly" in {

      val cfgString =
        """
          |{
          |  name = "test"
          |  vendor = "activemq"
          |  from = "inQueue"
          |  listener = 4
          |}
        """.stripMargin

      val cfg = ConfigFactory.parseString(cfgString)

      val inbound = InboundConfig.create(idSvc, cfg).get

      inbound.name should be("test")
      inbound.from should be(JmsQueue("inQueue"))
      inbound.provider should be(empty)
      inbound.listener should be(4)
    }

    "initialize from a config with placeholders correctly" in {

      val cfgString =
        """
          |{
          |  name = "test"
          |  vendor = "sonic"
          |  provider = "$[[BlendedCountry]]_topic"
          |  from = "topic:$[[BlendedCountry]]$[[BlendedLocation]]:$[[BlendedCountry]].$[[BlendedLocation]].data.in"
          |  to = "bridge.data.in"
          |  listener = 4
          |}
        """.stripMargin

      val cfg = ConfigFactory.parseString(cfgString)

      val inbound = InboundConfig.create(idSvc, cfg).get

      inbound.name should be("test")
      inbound.from should be(JmsDurableTopic("de.09999.data.in", "de09999"))
      inbound.provider should be(Some("de_topic"))
      inbound.listener should be(4)
    }

    "initialize with optional headers correctly" in {
      val cfgString =
        """
          |{
          |  name = "test"
          |  vendor = "activemq"
          |  from = "inQueue"
          |  header : [
          |    {
          |      name : "ResourceType"
          |      expression : "Test"
          |    }
          |  ]
          |}
        """.stripMargin

      val cfg = ConfigFactory.parseString(cfgString)
      val inbound = InboundConfig.create(idSvc, cfg).get

      inbound.header should have size 1
      inbound.header.head should be(HeaderProcessorConfig("ResourceType", Some("Test"), true))
    }
  }
}
