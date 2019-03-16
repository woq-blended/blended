package blended.security.crypto

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
      throw new NoArgsProvidedException
    }

    val cs : ContainerCryptoSupport = BlendedCryptoSupport.initCryptoSupport(cmdLine.secret)

    cmdLine.plain.foreach { p =>
      cs.encrypt(p) match {
        case Failure(t) =>
          System.err.println(s"Could not encrypt [$p] : [${t.getMessage()}]")
        case Success(e) =>
          System.out.println(s"Encrypted value for [$p] : [$e]")
      }
    }
  }

  private class NoArgsProvidedException extends Exception("No command line arguments provided.")

  private class CmdLine {

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
      description = "The plain text to encrypt."
    )
    var _plain : String = _
    def plain = Option(_plain)
  }
}
