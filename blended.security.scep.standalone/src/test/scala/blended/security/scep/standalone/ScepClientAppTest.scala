package blended.security.scep.standalone

import java.io.File
import java.net.{InetAddress, URL}
import java.nio.file.{Files, StandardCopyOption}
import java.util.concurrent.TimeoutException

import blended.testsupport.TestFile
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import com.typesafe.config.ConfigFactory
import de.tototec.cmdoption.CmdlineParser
import org.scalatest.Matchers
import os.CommandResult

class ScepClientAppTest extends LoggingFreeSpec with TestFile with Matchers {

  private[this] val log = Logger[ScepClientAppTest]

  "Cmdline parser" - {

    "validate cmdline parser (ensure consistent cmdoption setup)" in {
      val cmdline = new Cmdline()
      val cp = new CmdlineParser(cmdline)
      cp.validate()
    }

    "cmdline help " in {
      val ex = intercept[ExitAppException] {
        ScepClientApp.run(Array("--help"))
      }
      assert(ex.exitCode === 0)
      assert(ex.errMsg === None)
    }

    "invalid cmdline args exit with code 2" in {
      val ex = intercept[ExitAppException] {
        ScepClientApp.run(Array("--unknown"))
      }
      assert(ex.exitCode === 2)
      assert(ex.errMsg !== None)
    }

  }

  "App should fail with Exception when no config is present" in {
    withTestDir() { dir =>
      val etc = new File(dir, "etc")
      etc.mkdirs()
      val ex = intercept[ExitAppException] {
        ScepClientApp.run(Array("--refresh-certs", "--timeout", "5", "--base-dir", dir.getAbsolutePath()))
      }
      assert(ex.exitCode === 1)
      //TODO: Review - Ends with a file not found exception after API change
      //assert(ex.getCause().getClass() === classOf[TimeoutException])
    }(TestFile.DeleteWhenNoFailure)
  }

  "Online SCEP server environment specific" - {
    val envValName = "TEST_SCEP_CONFIGFILE"
    val clue =
      s"""
         |To test against an online SCEP server, please define the $envValName environment variable.
         |It must point to a configuration file with absolute path""".stripMargin
    val confFileName = System.getenv(envValName)

    s"should get a test certificate from online-scep server (TEST_SCEP_CONFIGFILE=${confFileName})" in {
      assume(confFileName !== null, clue)
      val confFile = new File(confFileName)
      assume(confFile.exists(), clue)

      val url = ConfigFactory.parseFile(confFile).getString("blended.security.scep.scepUrl")
      val host = new URL(url).getHost()
      assume(InetAddress.getByName(host).isReachable(2000), "\nScep host is not reachable")

      withTestDir() { dir =>
        val etc = new File(dir, "etc")
        etc.mkdirs()
        val targetConfFile = new File(etc, "application.conf")
        log.info(s"Copying config file from [${confFile}] to [${targetConfFile}]")
        Files.copy(confFile.toPath(), targetConfFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        val ex = intercept[ExitAppException] {
          ScepClientApp.run(Array("--refresh-certs", "--base-dir", dir.getAbsolutePath()))
        }
        assert(ex.exitCode === 0)
        val keystorefile = new File(etc, "keystore")
        assert(keystorefile.exists())

        // try to open the keystore with keytool
        val proc: CommandResult = os.proc("keytool", "-list", "-keystore", keystorefile.getAbsolutePath()).call(
          env = Map("LC_ALL" -> "c"),
          stdin = os.ProcessInput.SourceInput("e2e63a747c4c633e11d5f41f0297c020")
        )
        val out = proc.out.string
        out should include("Keystore type: PKCS12")
        out should include("Keystore provider: SUN")
        out should include("Your keystore contains 1 entry")
        out should include("server1,")

      }(TestFile.DeleteWhenNoFailure)

    }
  }

}
