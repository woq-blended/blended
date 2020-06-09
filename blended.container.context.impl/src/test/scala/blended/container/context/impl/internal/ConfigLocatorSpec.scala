package blended.container.context.impl.internal

import blended.container.context.api.ContainerContext
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.updater.config.Profile
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.matchers.should.Matchers

class ConfigLocatorSpec extends LoggingFreeSpec
  with Matchers {

  private val cfgDir : String = BlendedTestSupport.projectTestOutput
  System.setProperty(Profile.Properties.PROFILE_PROPERTY_KEYS, "foo,bar,FOO,num,version,typeA,typeB,blended.country,blended.demoProp")
  System.setProperty("blended.home", BlendedTestSupport.projectTestOutput)
  System.setProperty("blended.container.home", BlendedTestSupport.projectTestOutput)
  System.setProperty("COUNTRY", "cc")

  val ctCtxt : ContainerContext = new ContainerContextImpl()

  "The config locator should" - {

    "Read and evaluate a config file without blended replacements correctly" in {

      val cfg : Config = ConfigLocator.safeConfig(cfgDir, "etc/plain.conf", ConfigFactory.empty(), ctCtxt)
      cfg.getString("foo") should be ("bar")
    }

    "Perform replacements within the config file defined by blended" in {

      val cfg : Config = ConfigLocator.safeConfig(cfgDir, "etc/replace.conf", ConfigFactory.empty(), ctCtxt)

      cfg.getString("foo") should be ("test")
      cfg.getString("nested.mykey") should be ("12345")
      cfg.getString("nested.other") should be ("1234")
      cfg.getString("nested.plain") should be ("test")
    }
  }
}
