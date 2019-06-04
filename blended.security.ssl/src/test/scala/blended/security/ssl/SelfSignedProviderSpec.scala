package blended.security.ssl

import java.math.BigInteger
import java.security.cert.X509Certificate

import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalacheck.Gen
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

class SelfSignedProviderSpec extends LoggingFreeSpec
  with Matchers
  with PropertyChecks
  with SecurityTestSupport
  with CertificateRequestBuilder
  with CertificateSigner {

  "The self signed certificate provider should" - {

    "create a self signed certificate with the hostname populated signed with it's own key" in {

      forAll(Gen.alphaNumStr) { n =>
        whenever(n.trim().nonEmpty) {

          val holder = createRootCertificate(cn = n).get
          val cert : X509Certificate = holder.chain.head

          cert.getSerialNumber() should be(new BigInteger("1"))
          cert.getIssuerDN().toString should be(cert.getSubjectX500Principal().toString)
          holder.chain should have size 1
        }
      }
    }

    "update a self signed certificate by maintaining the same key pair and increasing the serial number" in {

      val cnProvider : CommonNameProvider =
        new HostnameCNProvider("root")

      val provider : CertificateProvider =
        new SelfSignedCertificateProvider(selfSignedCfg(cnProvider))

      val cert : CertificateHolder = provider.refreshCertificate(None, cnProvider).get
      val certNew : CertificateHolder = provider.refreshCertificate(Some(cert), cnProvider).get

      val c1 : X509Certificate = cert.chain.head
      val c2 : X509Certificate = certNew.chain.head

      c1.getIssuerDN() should be(c1.getSubjectDN())
      c1.getSerialNumber() should be(new BigInteger("1"))
      cert.chain should have size 1

      c2.getIssuerDN() should be(c2.getSubjectDN())
      c2.getSerialNumber() should be(new BigInteger("2"))
      certNew.chain should have size 1

      c1.getPublicKey() should equal(c2.getPublicKey())
      cert.privateKey should equal(certNew.privateKey)
    }

    "requires a private key in in the old key to refresh" in {

      val cnProvider : CommonNameProvider =
        new HostnameCNProvider("root")

      val provider : CertificateProvider =
        new SelfSignedCertificateProvider(selfSignedCfg(cnProvider))

      val cert : CertificateHolder = provider.refreshCertificate(None, cnProvider).get
      val pubOnly : CertificateHolder = cert.copy(privateKey = None)

      intercept[NoPrivateKeyException] {
        provider.refreshCertificate(Some(pubOnly), cnProvider).get
      }
    }
  }

}
