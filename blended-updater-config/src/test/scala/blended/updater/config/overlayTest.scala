package blended.updater.config

import org.scalatest.FreeSpec
import org.scalatest.Matchers
import java.io.File

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

  "OverlayConfig validation" - {

    "detects overlays with same name" in {
      val o1_1 = OverlayConfig("overlay1", "1")
      val o1_2 = OverlayConfig("overlay1", "2")
      val o2_1 = OverlayConfig("overlay2", "1")
      val overlays = LocalOverlays(overlays = List(o1_1, o1_2, o2_1), profileDir = new File("."))
      overlays.validate() should have size 1
      overlays.validate() shouldEqual Seq("More than one overlay with name 'overlay1' detected")
    }
    

    "Multiple overlays with same name should not be allowed" in {
      pending
    }

    "Overlays with conflicting generators should not be allowed" in {
      pending
    }

  }

  "OverlayConfig file generator" - {

  }
}