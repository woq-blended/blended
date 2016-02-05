package blended.updater.config

import org.scalatest.FreeSpec
import org.scalatest.Matchers
import java.io.File
import com.typesafe.config.ConfigFactory

class OverlaysTest extends FreeSpec with Matchers {

  "An empty LocalOverlays" - {
    "materializes not into the same directory" in {
      val dir = new File(".")
      val oDir = LocalOverlays.materializedDir(overlays = Nil, profileDir = dir)
      oDir shouldBe dir
    }
  }

  "A non-empty LocalOverlays" - {
    "materializes into a sub directory" in {
      val dir = new File(".")
      val oDir = LocalOverlays.materializedDir(overlays = List(OverlayRef("o", "1")), profileDir = dir)
      oDir.getPath() startsWith dir.getPath()
      oDir.getPath().length() > dir.getPath().length()
    }

    "materializes into same sub dir even if the overlays have different order" in {
      val dir = new File(".")
      val o1Dir = LocalOverlays.materializedDir(overlays = List(OverlayRef("o", "1"), OverlayRef("p", "1")), profileDir = dir)
      val o2Dir = LocalOverlays.materializedDir(overlays = List(OverlayRef("p", "1"), OverlayRef("o", "1")), profileDir = dir)
      o1Dir shouldBe o2Dir
    }
  }

  "LocalOverlays validation" - {
    "detects overlays with same name" in {
      val o1_1 = OverlayConfig("overlay1", "1")
      val o1_2 = OverlayConfig("overlay1", "2")
      val o2_1 = OverlayConfig("overlay2", "1")
      val overlays = LocalOverlays(overlays = List(o1_1, o1_2, o2_1), profileDir = new File("."))
      overlays.validate() shouldEqual Seq("More than one overlay with name 'overlay1' detected")
    }

    "detects overlays with conflicting generators" in {
      val config1 = ConfigFactory.parseString("key=val1")
      val o1 = OverlayConfig(
        name = "o1", version = "1",
        generatedConfigs = List(
          GeneratedConfig(configFile = "etc/application_overlay.conf", config = config1)
        )
      )
      val config2 = ConfigFactory.parseString("key=val2")
      val o2 = OverlayConfig(
        name = "o2", version = "1",
        generatedConfigs = List(
          GeneratedConfig(configFile = "etc/application_overlay.conf", config = config2)
        )
      )
      val overlays = LocalOverlays(overlays = List(o1, o2), profileDir = new File("."))
      overlays.validate() should have size 1
      overlays.validate() shouldEqual Seq("Double defined config key found: key")

    }
  }

  "OverlayConfig validation" - {
    "detects conflicting generators" in {
      val config1 = ConfigFactory.parseString("key=val1")
      val config2 = ConfigFactory.parseString("key=val2")
      val overlay = OverlayConfig(
        name = "o", version = "1",
        generatedConfigs = List(
          GeneratedConfig(configFile = "etc/application_overlay.conf", config = config1),
          GeneratedConfig(configFile = "etc/application_overlay.conf", config = config2)
        )
      )
      overlay.validate() shouldEqual Seq("Double defined config key found: key")
    }

  }

  "OverlayConfig file generator" - {

  }
}