package blended.launcher.config

import org.scalatest.FreeSpec
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigFactory
import blended.launcher.config.LauncherConfig.read
import blended.launcher.config.LauncherConfig.toConfig

class LauncherConfigTest extends FreeSpec {

  val minimalConfig = """
    |startLevel = 10
    |defaultStartLevel = 20
    |frameworkBundle = "framework.jar"
    |bundles = []
    |""".stripMargin

  "Minimal config" - {
    "read() must succeed" in {
      val config = LauncherConfig.read(ConfigFactory.parseString(minimalConfig))
    }

    val lines = minimalConfig.trim().split("\n")
    0.to(lines.size - 1).foreach { n =>
      "without line " + n + " must fail" in {
        val config = lines.take(n) ++ lines.drop(n + 1)
        intercept[ConfigException.ValidationFailed] {
          LauncherConfig.read(ConfigFactory.parseString(config.mkString("\n")))
        }
      }
    }

    "read() -> toConfig() -> read() must result in same config" in {
      import LauncherConfig._
      val config = read(ConfigFactory.parseString(minimalConfig))
      assert(config === read(toConfig(config)))
    }
  }

  "Complex config" - {

    "read() -> toConfig() -> read() must result in same config" in {
      import LauncherConfig._

      val config = """
        |frameworkBundle = framework-1.0.0.jar
        |startLevel = 10
        |defaultStartLevel = 20
        |bundles = [
        |  {
        |    location = "bundle1-1.0.0.jar"
        |    start = true
        |    startLevel = 5
        |  },
        |  {
        |    location = "bundle2-1.1.0.jar"
        |    start = true
        |  },
        |  {
        |    location = "bundle3-1.2.0.jar"
        |  }
        |]
        |systemProperties = {
        |  p1 = v1
        |  p2 = v2
        |}
        |frameworkProperties = {
        |  p3 = v3
        |  p4 = v4
        |}
        |branding = {
        |  p5 = v5
        |  p6 = v6
        |}
        |""".stripMargin

      val a = read(ConfigFactory.parseString(config))
      val b = read(toConfig(a))

      assert(a === b)
    }
  }

  "Config with placeholders" - {

    "read() -> toConfig must translate escaped placeholders" in {
      val config = """
       |frameworkBundle = framework-1.0.0.jar
       |startLevel = 10
       |defaultStartLevel = 20
       |bundles = [
       |  {
       |    location = "bundle1-1.0.0.jar"
       |    start = true
       |    startLevel = 5
       |  },
       |  {
       |    location = "bundle2-1.1.0.jar"
       |    start = true
       |  },
       |  {
       |    location = "bundle3-1.2.0.jar"
       |  }
       |]
       |systemProperties = {
       |  p1 = "$${VIPProperty}"
       |  p2 = "$${AnotherProperty}"
       |}
       |frameworkProperties = {
       |  p3 = v3
       |  p4 = v4
       |}
       |branding = {
       |  p5 = v5
       |  p6 = v6
       |}
       |""".stripMargin

      val a = read(ConfigFactory.parseString(config))
      val b = read(toConfig(a))

      assert(a === b)
    }
  }

}