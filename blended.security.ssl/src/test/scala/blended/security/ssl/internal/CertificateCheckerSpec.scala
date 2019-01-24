package blended.security.ssl.internal

import blended.security.ssl.{CertificateHolder, CertificateRequestBuilder, CertificateSigner, SecurityTestSupport}
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

class CertificateCheckerSpec extends LoggingFreeSpec
  with Matchers
  with SecurityTestSupport
  with CertificateRequestBuilder
  with CertificateSigner {

  "The certificate checker should" - {

    "Report failing certificate checks" in {

      val ks = jks("dummy.jks")
      val ms : MemoryKeystore = ks.loadKeyStore().get

      val root : CertificateHolder = createRootCertificate().get

      val newMs : MemoryKeystore = ms
        .update("root", createRootCertificate().get).get
        .update("host1", createHostCertificate("host1", root, 20).get).get
        .update("host2", createHostCertificate("host2", root, 8).get).get

      ks.saveKeyStore(newMs)

      val validator = new RemainingValidityChecker(10)

      val checkResults : Seq[CertificateCheckResult] = validator.checkCertificates(newMs).filter(r => !r.infoOnly)

      checkResults should have size(1)
      checkResults.head.alias should be ("host2")

    }
  }
}
