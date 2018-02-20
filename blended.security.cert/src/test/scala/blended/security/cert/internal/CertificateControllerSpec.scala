package blended.security.cert.internal

import java.io.File

import blended.testsupport.BlendedTestSupport.projectTestOutput
import org.scalatest.{FreeSpec, Matchers}

class CertificateControllerSpec extends FreeSpec
  with Matchers {

  def ctrlConfig(keyStore: String) : CertControllerConfig = CertControllerConfig(
    alias = "default",
    keyStore = new File(projectTestOutput, keyStore).getAbsolutePath,
    storePass = "andreas".toCharArray,
    keyPass = "123456".toCharArray,
    overwriteForFailure = false
  )

  def selfSignedConfig = SelfSignedConfig(
    "CN=test,O=blended,C=Germany", 2048, "SHA256withRSA", 10
  )

  def defaultProvider = new SelfSignedCertificateProvider(selfSignedConfig)

  "The Certificate Controller should" - {

    "retrieve a new certificate if no current keystore exists" in {

      val cfg = ctrlConfig("newKeystore")
      val keystore = new File(cfg.keyStore)
      if (keystore.exists()) keystore.delete()

      val ctrl = new CertificateController(cfg, defaultProvider)

      ctrl.checkCertificate().isSuccess should be (true)

    }

    "provide the current certificate if it is still vaild" in {
      pending
    }

    "refresh the current certificate if it is valid for less than x" in {
      pending
    }
  }
}
