package blended.security.crypto

import java.io.File
import java.net.URLClassLoader

import blended.testsupport.TestFile
import blended.testsupport.scalatest.LoggingFreeSpec
import de.tototec.cmdoption.CmdlineParser

class BlendedEncryptorSpec extends LoggingFreeSpec with TestFile {

  "command line parser validates" in {
    // see https://github.com/ToToTec/CmdOption#testing
    val cp = new CmdlineParser(new BlendedEncryptor.CmdLine())
    cp.validate()
  }

  "The Encryptor application" - {

    implicit val deletePolicy: TestFile.DeletePolicy = TestFile.DeleteWhenNoFailure


    class RunnerEnv(dir: os.Path) {
      val javaHome = System.getProperty("java.home")
      val cp = (this.getClass().getClassLoader() match {
        case cl: URLClassLoader =>
          cl.getURLs.map(u => new File(u.toURI()).getAbsolutePath())
        case _ =>
          Array()
      }).mkString(File.pathSeparator)
      assert(cp !== "")

      println("classpath: " + cp)
      println("javaHome: " + javaHome)
    }

    "should print usage with --help" in {
      val env = new RunnerEnv(os.pwd)
      val res = os.proc(
        s"${env.javaHome}/bin/java",
        "-cp", env.cp,
        "blended.security.crypto.BlendedEncryptor",
        "--help",
      ).call()

      assert(res.exitCode === 0)
    }

    "should run encryptor with text given via cmdline" in logException {
      withTestDir() { dir =>
        val secretFile = os.Path(dir) / "secret"
        os.write(secretFile, "secret")

        val env = new RunnerEnv(os.Path(dir))

        val res = os.proc(
          s"${env.javaHome}/bin/java",
          "-cp", env.cp,
          "blended.security.crypto.BlendedEncryptor",
          "--secret", secretFile.last,
          "--plain", "text"
        ).call(
          cwd = os.Path(dir)
        )

        val cs = BlendedCryptoSupport.initCryptoSupport(secretFile.toIO.getPath())
        assert(res.out.text().trim() === cs.encrypt("text").get)
        assert(res.exitCode === 0)
      }
    }

    "should run encryptor with text given via STDIN" in logException {
      withTestDir() { dir =>
        val secretFile = os.Path(dir) / "secret"
        os.write(secretFile, "secret")

        val env = new RunnerEnv(os.Path(dir))

        val res = os.proc(
          s"${env.javaHome}/bin/java",
          "-cp", env.cp,
          "blended.security.crypto.BlendedEncryptor",
          "--secret", secretFile.last,
          "--plain", "-"
        ).call(
          cwd = os.Path(dir),
          stdin = "text"
        )

        val cs = BlendedCryptoSupport.initCryptoSupport(secretFile.toIO.getPath())
        assert(res.out.text().trim() === cs.encrypt("text").get)
        assert(res.exitCode === 0)
      }
    }


  }

}
