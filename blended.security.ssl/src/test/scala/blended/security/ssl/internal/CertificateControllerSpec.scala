package blended.security.ssl.internal

import java.io.File
import java.math.BigInteger
import java.security.cert.X509Certificate
import scala.concurrent.duration._
import blended.testsupport.BlendedTestSupport.projectTestOutput
import org.scalatest.FreeSpec

import scala.util.{ Failure, Success }

class CertificateControllerSpec extends FreeSpec {

  private[this] val log = org.log4s.getLogger
  private[this] val subject = "CN=test, O=blended, C=Germany"
  private[this] val validDays: Int = 10

  private[this] val millisPerDay = 1.day.toMillis

  def ctrlConfig(keyStore: String): CertControllerConfig = CertControllerConfig(
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
      // initially create cert
      ctrl.checkCertificate()

      // check and update cert
      ctrl.checkCertificate() match {
        case Success(ks) =>
          val cert = ks.getCertificate("default").asInstanceOf[X509Certificate]
          val info = X509CertificateInfo(cert)

          log.info(s"$info")

          assert(info.serial === BigInt(1))
          assert(info.cn.equals(subject))
          assert(info.issuer.equals(subject))

          assert(info.notBefore.getTime() < System.currentTimeMillis())
          assert(info.notAfter.getTime() >= info.notBefore.getTime() + validDays * millisPerDay)

        case Failure(e) => fail(e.getMessage())
      }
    }

    s"refresh the current certificate if it is valid for less than a given threshold (${validDays} days)" in {

      val cfg = ctrlConfig("validKeystore")
      val keystore = new File(cfg.keyStore)
      if (keystore.exists()) keystore.delete()

      // initially create a cert which is valid but only for a short period of time
      val firstCertInfo = {
        val tempConfig = selfSignedConfig.copy(validDays = cfg.minValidDays - 1)
        val tempSelfProvider = new SelfSignedCertificateProvider(tempConfig)
        val tempController = new CertificateController(cfg, tempSelfProvider)
        val initKs = tempController.checkCertificate()
        assert(initKs.isSuccess)
        X509CertificateInfo(initKs.get.getCertificate("default").asInstanceOf[X509Certificate])
      }
      assert(firstCertInfo.notAfter.getTime() > System.currentTimeMillis())
      assert(firstCertInfo.notAfter.getTime() <= System.currentTimeMillis() + (validDays * millisPerDay))

      // check and update cert
      val ctrl = new CertificateController(cfg, defaultProvider)
      val secondKs = ctrl.checkCertificate()
      assert(secondKs.isSuccess)
      val secondCertInfo = X509CertificateInfo(secondKs.get.getCertificate("default").asInstanceOf[X509Certificate])

      log.info(s"$secondCertInfo")

      assert(firstCertInfo !== secondCertInfo, "The certificate was not renewed")

      assert(secondCertInfo.serial === BigInt(2))
      assert(secondCertInfo.cn.equals(subject))
      assert(secondCertInfo.issuer.equals(subject))

      assert(secondCertInfo.notBefore.getTime() < System.currentTimeMillis())
      assert(secondCertInfo.notAfter.getTime() >= secondCertInfo.notBefore.getTime() + validDays * millisPerDay)

    }
  }
}
