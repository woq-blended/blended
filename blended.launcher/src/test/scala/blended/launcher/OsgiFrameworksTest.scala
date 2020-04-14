package blended.launcher

import java.io.File

import blended.testsupport.TestFile
import blended.testsupport.scalatest.LoggingFreeSpec

class OsgiFrameworksTest extends LoggingFreeSpec
  with TestFile {

  implicit val deletePolicy = TestFile.DeleteWhenNoFailure

  "Minimal launcher with just the framework JAR" - {

    TestOsgiFrameworks.frameworks.foreach { case (name, file) =>
      name in {
        assert(new File(file).exists() == true, s"JAR file does not exists: ${file}")
        val launcherConfig =
          s"""frameworkBundle = "${file}"
             |startLevel = 10
             |defaultStartLevel = 4
             |frameworkProperties = {
             |  org.osgi.framework.storage.clean = onFirstInit
             |}
             |bundles = []
             |""".stripMargin

        withTestFile(launcherConfig) { configFile =>
          Launcher.run(Array(
            "--config", configFile.getAbsolutePath(),
            "--test"
          ))
        }
      }
    }
  }
}

