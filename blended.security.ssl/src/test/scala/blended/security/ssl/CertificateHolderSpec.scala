package blended.security.ssl

import java.security.{KeyPair, SignatureException}

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalacheck.Gen
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

import scala.util.{Success, Try}

class CertificateHolderSpec extends LoggingFreeSpec
  with Matchers
  with PropertyChecks
  with SecurityTestSupport
  with CertificateRequestBuilder
  with CertificateSigner {

  private def createChain(length : Int) : Try[CertificateHolder] = {

    def extendChain(c : CertificateHolder, l : Int) : Try[CertificateHolder] = {
      if (l == 0) {
        Success(c)
      } else {
        extendChain(createHostCertificate(s"host${length - l}", c).get, l - 1)
      }
    }

    extendChain(createRootCertificate().get, length - 1)
  }

  "The certificate holder should" - {

    "Refuse an empty chain" in {

      val p : KeyPair = kpg.generateKeyPair()
      intercept[EmptyCertificateChainException] {
        CertificateHolder.create(p, List.empty).get
      }
    }

    "Ensure the certificate chain does have a root certificate" in {

      val root : CertificateHolder = createRootCertificate().get
      val host : CertificateHolder = createHostCertificate("host", root).get

      intercept[MissingRootCertificateException] {
        CertificateHolder.create(host.publicKey, host.privateKey, host.chain.head :: Nil).get
      }
    }

    "Ensure the signature links are correct" in {
      val root : CertificateHolder = createRootCertificate().get
      val host : CertificateHolder = createHostCertificate("host", root).get
      val fakeRoot : CertificateHolder = createRootCertificate().get

      intercept[SignatureException] {
        CertificateHolder.create(host.publicKey, host.chain.head :: fakeRoot.chain.head :: Nil).get
      }
    }

    "Support chains of an arbitrary, yet reasonable length" in {

      val maxLength : Int = 10

      forAll(Gen.choose(1, maxLength)) { n =>
        assert(createChain(n).isSuccess)
      }
    }
  }
}
