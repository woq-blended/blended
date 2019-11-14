package blended.security.crypto

import scala.io.Source

import de.tototec.cmdoption.{CmdOption, CmdlineParser}
import scala.util.{Failure, Success}

object BlendedEncryptor {

  def main(args: Array[String]): Unit = run(args)

  def run(args : Array[String]) : Unit = {

    val cmdLine = new CmdLine()
    val cp = new CmdlineParser(cmdLine)
    cp.setAboutLine("Standalone client to encrypt plain Strings to be included in the config files.")
    cp.parse(args:_*)

    if (cmdLine.help || args.isEmpty) {
      cp.usage()
    } else {
      val cs : ContainerCryptoSupport = BlendedCryptoSupport.initCryptoSupport(cmdLine.secret)

      cmdLine.plain.foreach { p =>
        val text = p match {
          case "-" =>
            // read from stdin
            Source.stdin.getLines().mkString("\n")
          case x => x
        }

        cs.encrypt(text) match {
          case Failure(e) =>
            System.err.println(s"Could not encrypt value [${e.getMessage()}]")
          case Success(e) =>
            if(cmdLine.verbose) {
              System.out.println(s"Encrypted value for [$text] : [$e]")
            } else {
              System.out.println(e)
            }
        }
      }
    }
  }

  private class NoArgsProvidedException extends Exception("No command line arguments provided.")

  private[crypto] class CmdLine {

    @CmdOption(names = Array("--help", "-h"), description = "Show this help", isHelp = true)
    var help: Boolean = false

    @CmdOption(
      names = Array("--secret", "-s"),
      args = Array("file"),
      description = "The name of the file containing the secret for encrypting the plain string."
    )
    var _secret : String = _
    def secret : String = Option(_secret).getOrElse("secret")

    @CmdOption(
      names = Array("--plain", "-p"),
      args = Array("text"),
      description = "The plain text to encrypt. Use '-' to read text from STDIN"
    )
    var _plain : String = _
    def plain = Option(_plain)

    @CmdOption(
      names = Array("--verbose", "-v"),
      description = "Be more verbose"
    )
    var verbose: Boolean = false
  }
}
