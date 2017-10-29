package blended.launcher.config

import blended.launcher.config.LauncherConfig.{ read, toConfig }
import com.typesafe.config.{ ConfigException, ConfigFactory }
import org.scalatest.FreeSpec

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
      val toCfg = toConfig(config)
      val config2 = read(toCfg)
      assert(config === config2)
    }
  }

  "Complex config" - {

    "with prefix overlaying key in system settings" in {
      import LauncherConfig._

      val config = """
        |startLevel = 10
        |defaultStartLevel = 20
        |frameworkBundle = "framework.jar"
        |bundles = []
        |systemProperties {
        |  "org.osgi.service.http.port" = "8777"
        |  "org.osgi.service.http.port.secure" = "9443"
        |  org.osgi2.service {
        |    "http.port" = "8777"
        |    "http.port.secure" = "9443"
        |  }
        |}
        |""".stripMargin

      val a = read(ConfigFactory.parseString(config))
      val expectedSysProps = Map(
        "org.osgi.service.http.port" -> "8777",
        "org.osgi.service.http.port.secure" -> "9443",
        "org.osgi2.service.http.port" -> "8777",
        "org.osgi2.service.http.port.secure" -> "9443"
      )
      assert(a.systemProperties.toSet === expectedSysProps.toSet)
    }

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

      val fwp = b.frameworkProperties
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