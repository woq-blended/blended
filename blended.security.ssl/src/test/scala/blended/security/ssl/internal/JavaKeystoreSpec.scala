package blended.security.ssl.internal

import java.io.File

import blended.security.ssl.{CertificateHolder, CertificateRequestBuilder, CertificateSigner, SecurityTestSupport}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

class JavaKeystoreSpec extends LoggingFreeSpec
  with Matchers
  with SecurityTestSupport
  with CertificateRequestBuilder
  with CertificateSigner {

  "The Java Keystore should" - {

    def keystoreFile(name: String) : File = {
      val f = new File(BlendedTestSupport.projectTestOutput, name)
      if (f.exists()) {
        f.delete()
      }
      f
    }

    "Initialise to an empty store if the keystore file does not exist" in {

      val jks: JavaKeystore = new JavaKeystore(
        keystoreFile("dummy.jks"),
        "storepass".toCharArray,
        Some("storepass".toCharArray)
      )

      val ms: MemoryKeystore = jks.loadKeyStore().get

      ms.consistent should be(true)
      ms.certificates should be(empty)
    }

    "Initialize from a given keystore" in {
      val jks: JavaKeystore = new JavaKeystore(
        new File(BlendedTestSupport.projectTestOutput, "cacerts"),
        "changeit".toCharArray,
        None
      )

      val ms: MemoryKeystore = jks.loadKeyStore().get

      ms.consistent should be(true)
      ms.certificates should not be (empty)
    }

    "Allow to store a new certificate" in {
      val jks: JavaKeystore = new JavaKeystore(
        keystoreFile("dummy.jks"),
        "storepass".toCharArray,
        Some("storepass".toCharArray)
      )

      val ms1: MemoryKeystore = jks.loadKeyStore().get

      ms1.consistent should be(true)
      ms1.certificates should be(empty)

      val cert : CertificateHolder = createRootCertificate().get.copy(changed = true)
      val ms2 : MemoryKeystore = jks.saveKeyStore(ms1.update("test", cert).get).get

      ms2.certificates should have size(1)
      ms2.consistent should be (true)
      ms2.certificates.forall(_._2.privateKey.isDefined) should be (true)
    }
  }
}
