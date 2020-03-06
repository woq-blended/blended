package blended.container.context.impl.internal

import blended.container.context.api.ContainerContext
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.updater.config.RuntimeConfig
import blended.util.logging.Logger
import org.scalatest.Matchers
import blended.util.RichTry._

class ContainerContextImplSpec extends LoggingFreeSpec
  with Matchers {

  private val log : Logger = Logger[ContainerContextImplSpec]

  "The container context implementation should" - {

    "initialize correctly" in {

      System.setProperty("COUNTRY", "cc")
      System.setProperty(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS, "foo,bar,FOO,num,version,typeA,typeB,blended.country,blended.demoProp")
      System.setProperty("blended.home", BlendedTestSupport.projectTestOutput)
      val ctContext : ContainerContext = new ContainerContextImpl()

      log.info(s"Container Context : [$ctContext]")

      ctContext.properties should have size(9)
      ctContext.properties.get("foo") should be (Some("bar"))
      ctContext.properties.get("bar") should be (Some("test"))
      ctContext.properties.get("blended.country") should be (Some("cc"))

      ctContext.containerDirectory should be (BlendedTestSupport.projectTestOutput)
      ctContext.uuid should be ("context")

      ctContext.resolveString("$[[" + ContainerContext.containerId + "]]").unwrap should be(ctContext.uuid)
    }
  }

}
