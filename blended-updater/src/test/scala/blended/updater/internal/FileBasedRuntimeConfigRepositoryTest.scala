package blended.updater.internal

import org.scalatest.FreeSpecLike
import blended.updater.test.TestSupport
import java.io.File
import blended.updater.RuntimeConfig
import blended.updater.BundleConfig
import scala.collection.immutable._
import scala.io.Source

class FileBasedRuntimeConfigRepositoryTest
    extends FreeSpecLike
    with TestSupport {

  "Bootstrap" - {

    "survive missing config file" in {
      val file = File.createTempFile("test", "")
      file.delete()
      val repo = new FileBasedRuntimeConfigRepository(file, "prefix")
      repo.getAll()
      repo.readConfigs()
    }

    "survive empty config file" in {
      withTestFile("") { file =>
        val repo = new FileBasedRuntimeConfigRepository(file, "prefix")
        repo.getAll()
        repo.readConfigs()
      }
    }

    "add one config to an empty repo" in {
      withTestFile("") { file =>
        val repo = new FileBasedRuntimeConfigRepository(file, "prefix")
        repo.add(RuntimeConfig(
          name = "simple",
          version = "1.0.0",
          framework = BundleConfig(
            url = "url",
            jarName = "bundle1.jar",
            sha1Sum = "123",
            start = false,
            startLevel = None
          ),
          bundles = Seq(),
          startLevel = 10,
          defaultStartLevel = 10,
          frameworkProperties = Map(),
          systemProperties = Map()
        ))

        assert(Source.fromFile(file).getLines().mkString("\n").contains("simple"))
      }
    }

    val config = """
      |""".stripMargin

  }

}