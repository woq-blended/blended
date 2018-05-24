package blended.updater.config

import java.io.File

import blended.testsupport.TestFile
import blended.testsupport.TestFile.{ DeletePolicy, DeleteWhenNoFailure }
import com.typesafe.config.ConfigFactory
import org.scalatest.{ FreeSpec, Matchers }

import scala.collection.JavaConverters._
import blended.updater.config.util.ConfigPropertyMapConverter
import scala.util.Success
import org.scalatest.Args
import org.scalatest.Status

class OverlaysTest extends FreeSpec with Matchers with TestFile {

  private[this] val log = org.log4s.getLogger

  override def runTest(testName: String, args: Args): Status = {
    log.info("START runTest " + testName)
    try super.runTest(testName, args)
    finally log.info("FINISHED runTest " + testName)
  }

  implicit val deletePolicy: DeletePolicy = DeleteWhenNoFailure

  "Serialization of OverlayConfig" - {
    "deserializes a config file" in {
      withTestFile(
        """name: overlay
          |version: 1
          |configGenerator = [
          |  {
          |    file = file1
          |    config = {
          |      file1key: value
          |    }
          |  },
          |  {
          |    file = etc/file2.conf
          |    config = {
          |      file2key: value
          |    }
          |  }
          |]""".stripMargin
      ) { file =>
          val config = ConfigFactory.parseFile(file).resolve()
          val read = OverlayConfigCompanion.read(config)
          read.isSuccess shouldEqual true
          read.get.name shouldEqual "overlay"
          read.get.version shouldEqual "1"
          read.get.generatedConfigs.toSet shouldEqual Set(
            GeneratedConfigCompanion.create(
              "file1",
              ConfigFactory.parseMap(Map("file1key" -> "value").asJava)
            ),
            GeneratedConfigCompanion.create(
              "etc/file2.conf",
              ConfigFactory.parseMap(Map("file2key" -> "value").asJava)
            )
          )
        }
    }

    "serializes and desializes to the same config" in {
      val c = OverlayConfig(
        name = "overlay",
        version = "1",
        generatedConfigs = List(
          GeneratedConfigCompanion.create(
            "file1",
            ConfigFactory.parseMap(Map("file1key" -> "value").asJava)
          ),
          GeneratedConfigCompanion.create(
            "etc/file2",
            ConfigFactory.parseMap(Map("file2key" -> "value").asJava)
          )
        )
      )
      val read = OverlayConfigCompanion.read(OverlayConfigCompanion.toConfig(c))
      read.isSuccess shouldEqual true
      read.get.name shouldEqual "overlay"
      read.get.version shouldEqual "1"
      read.get.generatedConfigs.toSet shouldEqual Set(
        GeneratedConfigCompanion.create(
          "file1",
          ConfigFactory.parseMap(Map("file1key" -> "value").asJava)
        ),
        GeneratedConfigCompanion.create(
          "etc/file2",
          ConfigFactory.parseMap(Map("file2key" -> "value").asJava)
        )
      )
    }
  }

  "Overlay materialized dir for " - {
    "an empty LocalOverlays" - {
      "materializes not into the same directory" in {
        val dir = new File(".")
        val oDir = LocalOverlays.materializedDir(overlays = Nil, profileDir = dir)
        oDir shouldBe dir
      }
    }

    "a non-empty LocalOverlays" - {
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
  }

  "LocalOverlays validation" - {
    "detects overlays with same name" in {
      val o1_1 = OverlayConfig("overlay1", "1")
      val o1_2 = OverlayConfig("overlay1", "2")
      val o2_1 = OverlayConfig("overlay2", "1")
      val overlays = LocalOverlays(overlays = List(o1_1, o1_2, o2_1), profileDir = new File("."))
      overlays.validate() shouldEqual Seq("More than one overlay with name 'overlay1' detected")
    }

    "detects overlays with conflicting propetries" in {
      val o1 = OverlayConfig("o1", "1", properties = Map("P1" -> "V1"))
      val o2 = OverlayConfig("o2", "1", properties = Map("P1" -> "V2"))
      val overlays = LocalOverlays(overlays = List(o1, o2), profileDir = new File("."))
      overlays.validate() shouldEqual Seq("Duplicate property definitions detected. Property: P1 Occurences: o1-1, o2-1")
    }

    "detects overlays with conflicting generators" in {
      val config1 = ConfigFactory.parseString("key=val1")
      val o1 = OverlayConfig(
        name = "o1", version = "1",
        generatedConfigs = List(
          GeneratedConfigCompanion.create("etc/application_overlay.conf", config1)
        )
      )
      val config2 = ConfigFactory.parseString("key=val2")
      val o2 = OverlayConfig(
        name = "o2", version = "1",
        generatedConfigs = List(
          GeneratedConfigCompanion.create("etc/application_overlay.conf", config2)
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
          GeneratedConfigCompanion.create("etc/application_overlay.conf", config1),
          GeneratedConfigCompanion.create("etc/application_overlay.conf", config2)
        )
      )
      OverlayConfigCompanion.findCollisions(overlay.generatedConfigs) shouldEqual Seq("Double defined config key found: key")
    }

  }

  "LocalOverlays file generator" - {
    "generates nothing if no generators are present" in {
      val o1 = OverlayConfig("overlay1", "1")
      val o2 = OverlayConfig("overlay2", "1")
      withTestDir() { dir =>
        val overlays = LocalOverlays(List(o1, o2), dir)
        overlays.materialize().isSuccess shouldBe true
        overlays.materializedDir.listFiles() shouldBe null
      }
    }

    "generates one config file with merged content" in {
      val config1 = ConfigFactory.parseString("key1=val1")
      val o1 = OverlayConfig(
        name = "o1", version = "1",
        generatedConfigs = List(
          GeneratedConfigCompanion.create("etc/application_overlay.conf", config1)
        )
      )
      val config2 = ConfigFactory.parseString("key2=val2")
      val o2 = OverlayConfig(
        name = "o2", version = "1",
        generatedConfigs = List(
          GeneratedConfigCompanion.create("etc/application_overlay.conf", config2)
        )
      )
      withTestDir() { dir =>
        val overlays = LocalOverlays(List(o1, o2), dir)
        overlays.materialize().isSuccess shouldBe true
        val expectedEtcDir = new File(dir, "o1-1/o2-1/etc")
        overlays.materializedDir.listFiles() shouldBe Array(expectedEtcDir)
        val expectedConfigFile = new File(expectedEtcDir, "application_overlay.conf")
        expectedEtcDir.listFiles() shouldBe Array(expectedConfigFile)
        ConfigFactory.parseFile(expectedConfigFile).getString("key1") shouldBe "val1"
        ConfigFactory.parseFile(expectedConfigFile).getString("key2") shouldBe "val2"
      }
    }

    "generates nothing and aborts when configs have conflicts" in {
      val config1 = ConfigFactory.parseString("key1=val1")
      val o1 = OverlayConfig(
        name = "o1", version = "1",
        generatedConfigs = List(
          GeneratedConfigCompanion.create("etc/application_overlay.conf", config1)
        )
      )
      val config2 = ConfigFactory.parseString("key1=val2")
      val o2 = OverlayConfig(
        name = "o2", version = "1",
        generatedConfigs = List(
          GeneratedConfigCompanion.create("etc/application_overlay.conf", config2)
        )
      )
      withTestDir() { dir =>
        val overlays = LocalOverlays(List(o1, o2), dir)
        overlays.materialize().isFailure shouldBe true
      }
    }

    "preserves property-key formatting in merged generated content" in {
      val config1 = ConfigFactory.parseString("props {\n\"a.b.c\"=val1\n}")
      val o1 = OverlayConfig(
        name = "o1", version = "1",
        generatedConfigs = List(
          GeneratedConfigCompanion.create("etc/application_overlay.conf", config1)
        )
      )
      log.info("overlay config 1: " + o1)

      //       FIXME: also enable this
      //      val config2 = ConfigFactory.parseString("props {\n\"a.b.d\"=val2\n}")
      val config2 = ConfigFactory.parseString("props2 {\n\"a.b.d\"=val2\n}")
      val o2 = OverlayConfig(
        name = "o2", version = "1",
        generatedConfigs = List(
          GeneratedConfigCompanion.create("etc/application_overlay.conf", config2)
        )
      )
      log.info("overlay config 2: " + o2)

      withTestDir() { dir =>
        val overlays = LocalOverlays(List(o1, o2), dir)
        val expectedEtcDir = new File(dir, "o1-1/o2-1/etc")
        val expectedConfigFile = new File(expectedEtcDir, "application_overlay.conf")
        assert(overlays.materialize().map(_.toSet) === Success(Set(expectedConfigFile)))
        //        overlays.materialize().isSuccess shouldBe true
        //        overlays.materializedDir.listFiles() shouldBe Array(expectedEtcDir)
        //        expectedEtcDir.listFiles() shouldBe Array(expectedConfigFile)

        val expectedConfig = ConfigFactory.parseFile(expectedConfigFile)

        log.info("expectedConfig: " + expectedConfig)

        val props = ConfigPropertyMapConverter.getKeyAsPropertyMap(expectedConfig, "props", None)
        assert(props("a.b.c") === "val1")
        val props2 = ConfigPropertyMapConverter.getKeyAsPropertyMap(expectedConfig, "props2", None)
        assert(props2("a.b.d") === "val2")

        //        .getString("key1") shouldBe "val1"
        //        ConfigFactory.parseFile(expectedConfigFile).getString("key2") shouldBe "val2"
      }
    }

  }
}