package blended.container.context.impl.internal

import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.RichTry._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Matchers

class ConfigLocatorSpec extends LoggingFreeSpec
  with Matchers {

  private val cfgDir : String = BlendedTestSupport.projectTestOutput

  "The config locator should" - {

    "Read and evaluate a config file without blended replacements correctly" in {

      val cfg : Config = ConfigLocator.config(cfgDir, "plain.conf", ConfigFactory.empty()).unwrap
      cfg.getString("foo") should be ("bar")
    }

    "Perform replacements within the config file defined by blended" in {

      System.setProperty("foo", "bar")
      System.setProperty("akey", "avalue")

      val cfg : Config = ConfigLocator.config(cfgDir, "replace.conf", ConfigFactory.empty()).unwrap

      cfg.getString("foo") should be ("bar")
      cfg.getString("nested.mykey") should be ("avalue")
    }
  }
}
