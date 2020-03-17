package blended.security.scep.standalone

import java.io.{File, FileReader}
import java.util.concurrent.{TimeUnit, TimeoutException}

import blended.security.ssl.internal.PasswordHasher
import blended.util.logging.Logger
import de.tototec.cmdoption.{CmdlineParser, CmdlineParserException}
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.{PEMEncryptedKeyPair, PEMKeyPair, PEMParser}
import org.bouncycastle.pkcs.PKCS10CertificationRequest

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

object ScepClientApp {

  private[this] val log = Logger[ScepClientApp.type]

  /**
   * Entry point of the scep client app.
   * The app logic is done by [[run()]] which throws an [[ExitAppException]] to signal the exit code.
   * This will stop the running VM with [[java.lang.System#exit]]
   */
  def main(args : Array[String]) : Unit = {
    try {
      run(args)
      // default to 0
      // throw new ExitAppException(0)
    } catch {
      case e : ExitAppException =>
        e.errMsg.foreach { m =>
          Console.err.println(m)
        }
        Logger[ScepClientApp.type].debug(e)(s"About to exit VM from main-method with exit code [${e.exitCode}]")
        System.exit(e.exitCode.code) // ! Hard exit !
      case NonFatal(e) =>
        Logger[ScepClientApp.type].error(e)(s"An unexepected error occured.\nReason: ${e.getMessage()}")
        System.exit(ExitCode.InternalError.code) // ! Hard exit !
    }
  }

  /**
   * Run the scep client app.
   *
   * @param args
   * @throws ExitAppException The signal the exit code of the application
   */
  def run(args : Array[String]) : Unit = {
    val cmdline = new Cmdline()
    val cp = new CmdlineParser(cmdline)
    cp.setProgramName("java -jar scep-client.jar")
    cp.setAboutLine("Standalone SCEP client, which can create and update Java key stores from a remote SCEP server.")
    try {
      cp.parse(args : _*)
    } catch {
      case e : CmdlineParserException => throw new ExitAppException(ExitCode.InvalidCmdline, Option(e.getLocalizedMessage()), e)
    }

    if (cmdline.help || args.isEmpty) {
      cp.usage()
      throw new ExitAppException(ExitCode.Ok)
    }

    val salt = cmdline.salt.getOrElse("scep-client")

    cmdline.password.foreach { pass =>
      println(new PasswordHasher(salt).password(pass))
    }

    cmdline.infoFile.foreach { infoFile =>
      readInfoFile(infoFile)
    }

    cmdline.csrFile.foreach { csrFile =>
      readCsrFile(csrFile)
    }

    if (cmdline.refreshCerts) {
      refreshCert(
        salt,
        timeout = Duration(cmdline.timeout, TimeUnit.SECONDS),
        baseDir = new File(cmdline.baseDir.getOrElse(".")).getAbsoluteFile(),
        cmdline.expectRefresh
      )
    }

  }

  def readInfoFile(infoFileName : String) : Unit = {
    val file = new File(infoFileName).getAbsoluteFile()
    if (!file.exists() || !file.isFile()) {
      throw new RuntimeException(s"File does not exists: ${file}")
    }
    log.debug(s"About to parse file ${file}")
    val reader = new FileReader(file)
    val parser = new PEMParser(reader)
    val readObject = parser.readObject()
    log.debug(s"Parsed object [${readObject}]")
    readObject match {
      case encKeyPair : PEMEncryptedKeyPair =>
        log.debug(s"Got an encrypted key pair - need a password to decrypt")
      case keyPair : PEMKeyPair =>
        log.debug(s"Got an (unencrypted) key pair - no password needed")
      case cert : X509CertificateHolder =>
        log.debug(s"Got an certificate holder. subject [${cert.getSubject()}], version [${cert.getVersionNumber()}], extensions [${cert.getExtensions()}]")
      case csr : PKCS10CertificationRequest =>
        log.debug(s"Got a CSR. subject [${csr.getSubject()}], " +
          s"attributes [${csr.getAttributes().map(a => s"Attribute(type [${a.getAttrType()}], values [${a.getAttrValues}])").mkString(", ")}]")
    }
  }

  def readCsrFile(csrFile : String) : Unit = {
    val file = new File(csrFile).getAbsoluteFile()
    if (!file.exists() || !file.isFile()) {
      throw new RuntimeException(s"File does not exist: ${file}")
    }
    log.debug(s"About to parse file ${file}")
    val reader = new FileReader(file)
    val parser = new PEMParser(reader)
    val readObject = parser.readObject()
    log.debug(s"Parsed object [${readObject}]")
    readObject match {

      case csr : PKCS10CertificationRequest =>
        val subjectPublicKeyInfo = csr.getSubjectPublicKeyInfo()
        val details = Seq(
          "subject" -> csr.getSubject(),
          "attributes" -> csr.getAttributes().toList,
          "signature" -> csr.getSignature().toList,
          "signatureAlgorithm" -> csr.getSignatureAlgorithm(),
          "subjectPublicKeyInfo" -> subjectPublicKeyInfo,
          "subjectPublicKeyInfo.algorithm" -> subjectPublicKeyInfo.getAlgorithm(),
          "subjectPublicKeyInfo.algorithm.algorithm" -> subjectPublicKeyInfo.getAlgorithm().getAlgorithm(),
          "subjectPublicKeyInfo.algorithm.parameters" -> subjectPublicKeyInfo.getAlgorithm().getParameters(),
          "subjectPublicKeyInfo.publicKeyData" -> subjectPublicKeyInfo.getPublicKeyData()
        )

        log.debug(s"Got a CSR. Info:\n  ${details.map(t => s"${t._1}: ${t._2}").mkString("\n  ")}")

      case other =>
        val msg = s"File [${csrFile}] has no supported CSR file format"
        log.error(msg)
        throw new ExitAppException(ExitCode.Error, Some(msg))
    }
  }

  def refreshCert(salt : String, timeout : Duration, baseDir : File, expectCertChange: Boolean) : Unit = {
    implicit val executionContext = scala.concurrent.ExecutionContext.global
    val refresher = new CertRefresher(salt, baseDir)

    val checking = refresher.checkCert()

    try {
      Await.ready(checking, atMost = timeout).value.get match {
        case Success(r) =>
          log.debug(s"Successfully gathered certificates")
          refresher.stop()

          if (expectCertChange && r.changedAliases.isEmpty) {
            throw new ExitAppException(
              exitCode = ExitCode.NoCertsRefreshed,
              errMsg = Some(s"Successfully gathered unchanged certificates.")
            )
          }

          throw new ExitAppException(
            exitCode = ExitCode.Ok,
            errMsg = Some(s"Successfully refreshed certificates")
          )

        case Failure(e) =>
          refresher.stop()
          throw new ExitAppException(
            exitCode = ExitCode.Error,
            errMsg = Some(s"Error: Could not refresh certificates.\nReason: ${e.getMessage()}\nSee log file for details."),
            cause = e
          )
      }
    } catch {
      case e : TimeoutException =>
        refresher.stop()
        throw new ExitAppException(
          exitCode = ExitCode.Timeout,
          errMsg = Some(s"Error: Could not refresh certificates.\nReason: Timeout after [${timeout}]\nSee log file for details."),
          cause = e
        )
    }
  }
}

