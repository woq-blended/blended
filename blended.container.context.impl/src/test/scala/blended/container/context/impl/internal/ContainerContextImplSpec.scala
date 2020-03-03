package blended.container.context.impl.internal

import blended.container.context.api.ContainerContext
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.updater.config.RuntimeConfig
import org.scalatest.Matchers

class ContainerContextImplSpec extends LoggingFreeSpec
  with Matchers {

  "The container context implementation should" - {

    "initialize correctly" in {

      System.setProperty(RuntimeConfig.Properties.PROFILE_PROPERTY_KEYS, "foo,bar")
      System.setProperty("blended.home", BlendedTestSupport.projectTestOutput)
      val ctContext : ContainerContext = new ContainerContextImpl()

      ctContext.properties should have size(2)
      ctContext.properties.get("foo") should be (Some("bar"))
      ctContext.properties.get("bar") should be (Some("test"))

      ctContext.containerDirectory should be (BlendedTestSupport.projectTestOutput)
      ctContext.uuid should be ("context")
    }
  }

}
