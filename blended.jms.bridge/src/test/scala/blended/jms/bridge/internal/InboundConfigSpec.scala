package blended.jms.bridge.internal

import java.io.File

import blended.container.context.api.ContainerContext
import blended.jms.utils.{JmsDurableTopic, JmsQueue}
import blended.streams.processor.HeaderProcessorConfig
import blended.testsupport.BlendedTestSupport
import blended.testsupport.pojosr.{PojoSrTestHelper, SimplePojoContainerSpec}
import blended.testsupport.scalatest.LoggingFreeSpecLike
import blended.util.RichTry._
import com.typesafe.config.ConfigFactory
import org.osgi.framework.BundleActivator
import org.scalatest.Matchers

import scala.concurrent.duration._

class InboundConfigSpec extends SimplePojoContainerSpec
  with LoggingFreeSpecLike
  with Matchers
  with PojoSrTestHelper {

  private[this] implicit val timeout : FiniteDuration = 3.seconds
  override def baseDir: String = new File(BlendedTestSupport.projectTestOutput, "container").getAbsolutePath()

  override def bundles: Seq[(String, BundleActivator)] = Seq.empty

  protected val ctCtxt : ContainerContext = mandatoryService[ContainerContext](registry)(None)

  "The inbound config should" - {

    System.setProperty("BlendedCountry", "de")
    System.setProperty("BlendedLocation", "09999")

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

      val inbound = InboundConfig.create(ctCtxt, cfg).unwrap

      // scalastyle:off magic.number
      inbound.name should be("test")
      inbound.from should be(JmsQueue("inQueue"))
      inbound.provider should be(empty)
      inbound.listener should be(4)
      // scalastyle:on magic.number
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

      val inbound = InboundConfig.create(ctCtxt, cfg).unwrap

      // scalastyle:off magic.number
      inbound.name should be("test")
      inbound.from should be(JmsDurableTopic("de.09999.data.in", "de09999"))
      inbound.provider should be(Some("de_topic"))
      inbound.listener should be(4)
      // scalastyle:on magic.number
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
      val inbound = InboundConfig.create(ctCtxt, cfg).unwrap

      inbound.header should have size 1
      inbound.header.head should be(HeaderProcessorConfig("ResourceType", Some("Test"), overwrite = true))
    }
  }

}
