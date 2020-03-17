package blended.security.scep.standalone

import java.io.File
import java.net.{InetAddress, URL}
import java.nio.file.{Files, StandardCopyOption}
import java.util.concurrent.{TimeUnit, TimeoutException}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.NonFatal

import blended.testsupport.TestFile
import blended.testsupport.retry.Retry
import blended.testsupport.scalatest.LoggingFreeSpec
import blended.util.logging.Logger
import com.typesafe.config.ConfigFactory
import de.tototec.cmdoption.{CmdOption, CmdlineParser}
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
      assert(ex.exitCode === ExitCode.Ok)
      assert(ex.errMsg === None)
    }

    "invalid cmdline args exit with code 2" in {
      val ex = intercept[ExitAppException] {
        ScepClientApp.run(Array("--unknown"))
      }
      assert(ex.exitCode === ExitCode.InvalidCmdline)
      assert(ex.errMsg !== None)
    }

    "hardcoded description contains correct exit code" in {
      val anno = classOf[Cmdline].getDeclaredFields()
        .flatMap(f => Option(f.getAnnotation(classOf[CmdOption])))
        .find(a => a.names().contains("--expect-refresh"))
      anno.get.description() should endWith(s" ${ExitCode.NoCertsRefreshed.code}")
    }

  }

  "App should fail with Exception when no config is present" in {
    withTestDir() { dir =>
      val etc = new File(dir, "etc")
      etc.mkdirs()
      val ex = intercept[ExitAppException] {
        ScepClientApp.run(Array("--refresh-certs", "--timeout", "5", "--base-dir", dir.getAbsolutePath()))
      }
      assert(ex.exitCode === ExitCode.Error)
      //TODO: Review - Ends with a file not found exception after API change
      //assert(ex.getCause().getClass() === classOf[TimeoutException])
    }(TestFile.DeleteWhenNoFailure)
  }

  val envValName = "TEST_SCEP_CONFIGFILE"
  val confFileName = System.getenv(envValName)

  s"Online SCEP server environment specific (${envValName}=${confFileName})" - {
    val clue =
      s"""
         |To test against an online SCEP server, please define the $envValName environment variable.
         |It must point to a configuration file with absolute path""".stripMargin

    def testOnlineTestServer(checkRefreshedCert: Boolean): Unit = {

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
          ScepClientApp.run(
            Array("--refresh-certs", "--base-dir", dir.getAbsolutePath()) ++
            Array("--expect-refresh").filter(_ => checkRefreshedCert)
          )
        }
        // initial request should refresh/initial get a cert with exit code 0
        assert(ex.exitCode === ExitCode.Ok)
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

        if(checkRefreshedCert) {
          val ex = intercept[ExitAppException] {
            ScepClientApp.run(
              Array("--refresh-certs", "--expect-refresh", "--base-dir", dir.getAbsolutePath())
            )
          }
          // sub-sequent refresh should detect no change and exit with exit code 5
          assert(ex.exitCode === ExitCode.NoCertsRefreshed)
        }

      }(TestFile.DeleteWhenNoFailure)
    }

    s"should get a test certificate from online scep server" in {
      // first test certs takes sometimes very long
      Try(testOnlineTestServer(checkRefreshedCert = false))
//        .recoverWith{ case NonFatal(_) => Thread.sleep(3000); Try(testOnlineTestServer(false))}
        .get
    }

    s"should report a non-refreshed certificate with exit code ${ExitCode.NoCertsRefreshed.code}" in {
      // first test certs takes sometimes very long
      Try(testOnlineTestServer(checkRefreshedCert = true))
//        .recoverWith{ case NonFatal(_) => Thread.sleep(3000); Try(testOnlineTestServer(true))}
        .get
    }

  }

}
