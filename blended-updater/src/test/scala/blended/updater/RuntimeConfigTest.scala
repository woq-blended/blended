package blended.updater

import org.scalatest.FreeSpecLike
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigException

class RuntimeConfigTest extends FreeSpecLike {

  "Minimal config" - {

    val minimal = """
      |name = name
      |version = 1.0.0
      |framework = { url = "http://example.org", jarName = "bundle1.jar", sha1Sum = sum }
      |startLevel = 10
      |defaultStartLevel = 10
      |""".stripMargin

    "read" in {
      val config = RuntimeConfig.read(ConfigFactory.parseString(minimal))
    }

    val lines = minimal.trim().split("\n")
    0.to(lines.size - 1).foreach { n =>
      "without line " + n + " must fail" in {
        val config = lines.take(n) ++ lines.drop(n + 1)
        intercept[ConfigException.ValidationFailed] {
          RuntimeConfig.read(ConfigFactory.parseString(config.mkString("\n")))
        }
      }
    }

    "read -> toConfig -> read must result in same config" in {
      import RuntimeConfig._
      val config = read(ConfigFactory.parseString(minimal))
      assert(config === read(toConfig(config)))
    }
  }

}