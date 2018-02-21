package blended.security.cert.internal

import java.io.File
import java.math.BigInteger
import java.security.cert.X509Certificate
import scala.concurrent.duration._

import blended.testsupport.BlendedTestSupport.projectTestOutput
import org.scalatest.FreeSpec

import scala.util.{Failure, Success}

class CertificateControllerSpec extends FreeSpec {

  private[this] val log = org.log4s.getLogger
  private[this] val subject = "CN=test, O=blended, C=Germany"
  private[this] val validDays : Int = 10

  private[this] val millisPerDay = 1.day.toMillis

  def ctrlConfig(keyStore: String) : CertControllerConfig = CertControllerConfig(
    alias = "default",
    keyStore = new File(projectTestOutput, keyStore).getAbsolutePath,
    storePass = "andreas".toCharArray,
    keyPass = "123456".toCharArray,
    minValidDays = 5,
    overwriteForFailure = false
  )

  def selfSignedConfig = SelfSignedConfig(
    subject, 2048, "SHA256withRSA", validDays
  )

  def defaultProvider = new SelfSignedCertificateProvider(selfSignedConfig)

  "The Certificate Controller should" - {

    "retrieve a new certificate if no current keystore exists" in {

      val cfg = ctrlConfig("newKeystore")
      val keystore = new File(cfg.keyStore)
      if (keystore.exists()) keystore.delete()

      val ctrl = new CertificateController(cfg, defaultProvider)

      ctrl.checkCertificate() match {
        case Success(ks) =>
          val cert = ks.getCertificate("default").asInstanceOf[X509Certificate]
          val info = X509CertificateInfo(cert)

          log.info(s"$info")

          assert(info.serial.bigInteger === BigInteger.ONE)
          assert(info.cn.equals(subject))
          assert(info.issuer.equals(subject))

          assert(info.notBefore.getTime() < System.currentTimeMillis())
          assert(info.notAfter.getTime() >= info.notBefore.getTime() + validDays * millisPerDay)

        case Failure(e) => fail(e.getMessage())
      }
    }

    "provide the current certificate if it is still vaild" in {

      val cfg = ctrlConfig("validKeystore")
      val keystore = new File(cfg.keyStore)
      if (keystore.exists()) keystore.delete()

      val ctrl = new CertificateController(cfg, defaultProvider)
      ctrl.checkCertificate()

      ctrl.checkCertificate() match {
        case Success(ks) =>
          val cert = ks.getCertificate("default").asInstanceOf[X509Certificate]
          val info = X509CertificateInfo(cert)

          log.info(s"$info")

          assert(info.serial.bigInteger === BigInteger.ONE)
          assert(info.cn.equals(subject))
          assert(info.issuer.equals(subject))

          assert(info.notBefore.getTime() < System.currentTimeMillis())
          assert(info.notAfter.getTime() >= info.notBefore.getTime() + validDays * millisPerDay)

        case Failure(e) => fail(e.getMessage())
      }
    }

    "refresh the current certificate if it is valid for less than x" in {
      pending
    }
  }
}
