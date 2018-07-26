package blended.security.scep.standalone

import scala.util.Failure
import scala.util.Success

import blended.security.ssl.internal.PasswordHasher
import de.tototec.cmdoption.CmdlineParser

object ScepClientApp {

  /**
   * Entry point of the scep client app.
   * This will stop the running VM with [[java.lang.System#exit]]
   */
  def main(args: Array[String]): Unit = {
    val cmdline = new Cmdline()
    val cp = new CmdlineParser(cmdline)
    cp.setProgramName("java -jar scep-client.jar")
    cp.setAboutLine("Standalone SCEP client, which can create and update Java key stores from a remote SCEP server.")
    cp.parse(args: _*)

    if (cmdline.help || args.isEmpty) {
      cp.usage()
      System.exit(0) // ! Hard exit !
    }

    val salt = cmdline.salt.getOrElse("scep-client")

    cmdline.password.foreach { pass =>
      println(new PasswordHasher(salt).password(pass))
    }

    if (cmdline.refreshCerts) {
      implicit val executionContext = scala.concurrent.ExecutionContext.global
      val refresher = new CertRefresher(salt)
      refresher.checkCert().onComplete {
        case Success(r) =>
          println(s"Successfully refreshed certificates")
          refresher.stop()

          System.exit(0) // ! Hard exit !

        case Failure(e) =>
          println(s"Error: Could not refresh certificates.\nReason: ${e.getMessage()}\nSee log file for details.")
          refresher.stop()

          System.exit(1) // ! Hard exit !
      }
    }
  }
}

