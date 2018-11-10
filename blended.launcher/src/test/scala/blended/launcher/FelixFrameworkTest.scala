package blended.launcher

import org.scalatest.FreeSpec
import java.io.File
import blended.testsupport.TestFile
import blended.testsupport.BlendedTestSupport.projectTestOutput

class FelixFrameworkTest extends FreeSpec with TestFile {

  implicit val deletePolicy = TestFile.DeleteWhenNoFailure

  "Launch with Apache Felix" - {

    val versions = Seq(
      "5.0.0",
      "5.6.10"
    )

    versions.foreach { v =>
      s"minimal: just the framework version ${v}" in {

        val launcherConfig = (
          "repo = \"" + new File(projectTestOutput + "/../../test-osgi").getAbsolutePath() + """"
        |frameworkBundle = ${repo}"/org.apache.felix.framework-""" + v + """.jar"
        |startLevel = 10
        |defaultStartLevel = 4
        |frameworkProperties = {
        |  org.osgi.framework.storage.clean = onFirstInit
        |}
        |bundles = []
        |""").stripMargin

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

//withTestDir() { dir =>
//        // FIXME: This is bad!!!
//        // Please at least ensure, we run the test in a dedicated JVM
//        System.err.println("Mutating system property: blended.home")
//        System.setProperty("blended.home", dir.getAbsolutePath)
//        withTestFile(launcherConfig) { configFile =>
//          val retVal = Launcher.run(Array(
//            "--config", configFile.getAbsolutePath(),
//            "--test",
//            "--init-container-id"
//          ))
//          assert(retVal === 0)
//        }
//      }

