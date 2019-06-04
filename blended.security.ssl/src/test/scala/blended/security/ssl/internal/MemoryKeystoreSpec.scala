package blended.security.ssl.internal

import java.math.BigInteger

import blended.security.ssl._
import blended.testsupport.scalatest.LoggingFreeSpec
import javax.security.auth.x500.X500Principal
import org.scalatest.Matchers

import scala.util.{Failure, Try}

class MemoryKeystoreSpec extends LoggingFreeSpec
  with Matchers
  with SecurityTestSupport
  with CertificateRequestBuilder
  with CertificateSigner {

  private val cnProvider : CommonNameProvider = new HostnameCNProvider("host")

  private val certCfg : CertificateConfig = CertificateConfig(
    provider = "selfsigned",
    alias = "cert",
    minValidDays = validDays,
    cnProvider = cnProvider
  )

  "The Memory key store" - {

    "calculate the next certificate timeout correctly from an empty store" in {

      val start : Long = System.currentTimeMillis()

      val ms : MemoryKeystore = new MemoryKeystore(Map.empty)
      val timeout : Long = ms.nextCertificateTimeout().get.getTime()

      assert(start <= timeout && timeout <= System.currentTimeMillis())
    }

    "calculate the next certificate timeout correctly from an non-empty store" in {

      val start : Long = System.currentTimeMillis() + (validDays - 1) * millisPerDay
      val end : Long = start + 2 * millisPerDay

      val root : CertificateHolder = createRootCertificate("root").get
      val ms : MemoryKeystore = new MemoryKeystore(Map("root" -> root))
      val timeout : Long = ms.nextCertificateTimeout().get.getTime()

      assert(start <= timeout && timeout <= end)
    }

    "not allow inconsistent updates" in {

      val root : CertificateHolder = createRootCertificate("root").get
      val ms : MemoryKeystore = new MemoryKeystore(Map("root" -> root))

      val host : CertificateHolder = createHostCertificate("host", root, validDays).get
        .copy(privateKey = None)

      intercept[InconsistentKeystoreException] {
        ms.update("host", host).get
      }
    }

    "should retrieve certificates that are not yet present in the keystore" in {

      val provider : CertificateProvider = new SelfSignedCertificateProvider(selfSignedCfg(cnProvider))

      val ms : MemoryKeystore = new MemoryKeystore(Map.empty)

      val newMs = ms.refreshCertificates(List(certCfg), Map("selfsigned" -> provider)).get

      newMs.certificates should have size 1
      newMs.certificates.keys should contain("cert")

      newMs.changedAliases should be(List("cert"))
    }

    "should not update the certificates if the required provider can't be found (empty store)" in {
      val ms : MemoryKeystore = new MemoryKeystore(Map.empty)
      val newMs = ms.refreshCertificates(List(certCfg), Map.empty).get

      newMs.certificates should be(empty)
      newMs.changedAliases should be(empty)
    }

    "should not update the certificates if the required provider can't be found (non-empty store)" in {

      // Create a root certificate with a validity less than validDays to trigger a refresh
      val root : CertificateHolder = createRootCertificate(validDays = validDays / 2).get

      val ms : MemoryKeystore = new MemoryKeystore(Map(certCfg.alias -> root))
      val newMs = ms.refreshCertificates(List(certCfg), Map.empty).get

      newMs.certificates should have size 1
      newMs.changedAliases should be(empty)

      newMs.certificates.values.head.chain.head.getSerialNumber should be(BigInteger.ONE)
    }

    "should update the certificates that are about to expire" in {

      val provider : CertificateProvider = new SelfSignedCertificateProvider(selfSignedCfg(cnProvider))

      val timingOut : CertificateHolder = createRootCertificate("timingOut", validDays = validDays / 2).get
      val stillValid : CertificateHolder = createRootCertificate("stillValid", validDays = validDays * 2).get

      val ms : MemoryKeystore = new MemoryKeystore(Map("timingOut" -> timingOut, "stillValid" -> stillValid))

      val certCfgs : List[CertificateConfig] = List(
        certCfg.copy(alias = "timingOut"),
        certCfg.copy(alias = "stillValid")
      )

      val newMs = ms.refreshCertificates(certCfgs, Map("selfsigned" -> provider)).get

      newMs.certificates should have size (2)
      newMs.changedAliases should be(List("timingOut"))

      newMs.certificates.get("stillValid").get.chain.head.getSerialNumber should be(BigInteger.ONE)
      newMs.certificates.get("timingOut").get.chain.head.getSerialNumber should be(new BigInteger("2"))
    }

    "should not update the keystore if the refresh has failed" in {

      val provider : CertificateProvider = new SelfSignedCertificateProvider(selfSignedCfg(cnProvider)) {

        override def refreshCertificate(existing : Option[CertificateHolder], cnProvider : CommonNameProvider) : Try[CertificateHolder] =
          Failure(new Exception("Boom"))
      }

      val timingOut : CertificateHolder = createRootCertificate("timingOut", validDays = validDays / 2).get
      val stillValid : CertificateHolder = createRootCertificate("stillValid", validDays = validDays * 2).get

      val ms : MemoryKeystore = new MemoryKeystore(Map("timingOut" -> timingOut, "stillValid" -> stillValid))

      val certCfgs : List[CertificateConfig] = List(
        certCfg.copy(alias = "timingOut"),
        certCfg.copy(alias = "stillValid")
      )

      val newMs = ms.refreshCertificates(certCfgs, Map("selfsigned" -> provider)).get

      newMs.certificates should have size (2)
      newMs.changedAliases should be(List.empty)

      newMs.certificates.get("stillValid").get.chain.head.getSerialNumber should be(BigInteger.ONE)
      newMs.certificates.get("timingOut").get.chain.head.getSerialNumber should be(BigInteger.ONE)
    }

    "should allow to look up a certificate by the subjectPrincipal" in {

      val c1 : CertificateHolder = createRootCertificate("cert1", validDays = validDays).get
      val c2 : CertificateHolder = createRootCertificate("cert2", validDays = validDays).get

      val ms : MemoryKeystore = new MemoryKeystore(Map("cert1" -> c1, "cert2" -> c2))

      val notFound : Option[CertificateHolder] = ms.findByPrincipal(new X500Principal("CN=foo"))
      val cert : Option[CertificateHolder] = ms.findByPrincipal(new X500Principal("CN=cert1"))

      notFound should be(empty)
      cert should be(defined)

      assert(cert.forall { ch =>
        ch.publicKey.equals(c1.publicKey)
      })
    }
  }
}
