package blended.launcher

import java.io.File

import blended.testsupport.TestFile
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.testsupport.BlendedTestSupport.projectTestOutput

class EquinoxFrameworkTest extends LoggingFreeSpec
  with TestFile {

  implicit val deletePolicy = TestFile.DeleteWhenNoFailure

  "Launch with Equinox" - {

    val versions = Seq(
      "3.10.100.v20150529-1857",
      "3.10.0.v20140606-1445",
      "3.12.50"
    )

    versions.foreach { v =>

      s"minimal: just the framework version ${v} " in {
        val launcherConfig = (
          "repo = \"" + new File(projectTestOutput + "/../../test-osgi").getAbsolutePath() + """"
        |frameworkBundle = ${repo}"/org.eclipse.osgi-""" + v + """.jar"
        |startLevel = 10
        |defaultStartLevel = 4
        |frameworkProperties = {
        |  org.osgi.framework.storage.clean = onFirstInit
        |}
        |bundles = []
        |"""
        ).stripMargin

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

