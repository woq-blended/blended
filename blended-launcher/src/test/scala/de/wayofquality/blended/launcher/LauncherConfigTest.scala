package de.wayofquality.blended.launcher

import org.scalatest.FreeSpec
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigException

class LauncherConfigTest extends FreeSpec {

  val minimalConfig = """
    |startLevel = 10
    |defaultStartLevel = 20
    |frameworkBundle = "framework.jar"
    |bundles = []
""".stripMargin

  "Minimal config" - {
    "read must succeed" in {
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
  }

}