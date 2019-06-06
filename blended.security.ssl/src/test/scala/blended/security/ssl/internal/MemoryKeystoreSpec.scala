package blended.security.ssl.internal

import java.math.BigInteger

import blended.security.ssl._
import blended.testsupport.scalatest.LoggingFreeSpec
import javax.security.auth.x500.X500Principal
import org.scalatest.Matchers

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class MemoryKeystoreSpec extends LoggingFreeSpec
  with Matchers
  with SecurityTestSupport
  with CertificateRequestBuilder
  with CertificateSigner {

  private val cnProvider : CommonNameProvider = HostnameCNProvider("host")

  private val certCfg : CertificateConfig = CertificateConfig(
    provider = "selfsigned",
    alias = "cert",
    minValidDays = validDays,
    cnProvider = cnProvider
  )

  private def newHostCertificate(cn : String, issuedBy : CertificateHolder, days: Int) : CertificateHolder = try {
    createHostCertificate(cn, issuedBy, days).get
  } catch {
    case NonFatal(t) => fail(t)
  }

  private def newRootCertificate(cn : String, days : Int = validDays) : CertificateHolder = try {
    createRootCertificate(cn, days).get
  } catch {
    case NonFatal(t) => fail(t)
  }

  "The Memory key store" - {

    "calculate the next certificate timeout correctly from an empty store" in {

      val start : Long = System.currentTimeMillis()

      val ms : MemoryKeystore = MemoryKeystore(Map.empty)
      ms.nextCertificateTimeout() match {
        case Success(to) => assert(start <= to.getTime() && System.currentTimeMillis() >= to.getTime())
        case Failure(t) => fail(t)
      }
    }

    "calculate the next certificate timeout correctly from an non-empty store" in {

      val start : Long = System.currentTimeMillis() + (validDays - 1) * millisPerDay
      val end : Long = start + 2 * millisPerDay

      val root : CertificateHolder = newRootCertificate("root")
      val ms : MemoryKeystore = MemoryKeystore(Map("root" -> root))
      ms.nextCertificateTimeout() match {
        case Success(to) => assert(start <= to.getTime() && to.getTime() <= end)
        case Failure(t) => fail(t)
      }


    }

    "not allow inconsistent updates" in {

      val root : CertificateHolder = newRootCertificate("root")
      val ms : MemoryKeystore = MemoryKeystore(Map("root" -> root))

      val host : CertificateHolder = newHostCertificate("host", root, validDays).copy(privateKey = None)

      intercept[InconsistentKeystoreException] {
        ms.update("host", host).get
      }
    }

    "should retrieve certificates that are not yet present in the keystore" in {

      val provider : CertificateProvider = new SelfSignedCertificateProvider(selfSignedCfg(cnProvider))

      val ms : MemoryKeystore = MemoryKeystore(Map.empty)

      val newMs = ms.refreshCertificates(List(certCfg), Map("selfsigned" -> provider)).get

      newMs.certificates should have size 1
      newMs.certificates.keys should contain("cert")

      newMs.changedAliases should be(List("cert"))
    }

    "should not update the certificates if the required provider can't be found (empty store)" in {
      val ms : MemoryKeystore = MemoryKeystore(Map.empty)
      val newMs = ms.refreshCertificates(List(certCfg), Map.empty).get

      newMs.certificates should be(empty)
      newMs.changedAliases should be(empty)
    }

    "should not update the certificates if the required provider can't be found (non-empty store)" in {

      // Create a root certificate with a validity less than validDays to trigger a refresh
      val root : CertificateHolder = newRootCertificate(cn = "root", days = validDays / 2)

      val ms : MemoryKeystore = MemoryKeystore(Map(certCfg.alias -> root))
      val newMs = ms.refreshCertificates(List(certCfg), Map.empty).get

      newMs.certificates should have size 1
      newMs.changedAliases should be(empty)

      newMs.certificates.values.head.chain.head.getSerialNumber should be(BigInteger.ONE)
    }

    "should update the certificates that are about to expire" in {

      val provider : CertificateProvider = new SelfSignedCertificateProvider(selfSignedCfg(cnProvider))

      val timingOut : CertificateHolder = newRootCertificate("timingOut", days = validDays / 2)
      val stillValid : CertificateHolder = newRootCertificate("stillValid", days = validDays * 2)

      val ms : MemoryKeystore = MemoryKeystore(Map("timingOut" -> timingOut, "stillValid" -> stillValid))

      val certCfgs : List[CertificateConfig] = List(
        certCfg.copy(alias = "timingOut"),
        certCfg.copy(alias = "stillValid")
      )

      val newMs = ms.refreshCertificates(certCfgs, Map("selfsigned" -> provider)).get

      newMs.certificates should have size 2
      newMs.changedAliases should be(List("timingOut"))

      newMs.certificates("stillValid").chain.head.getSerialNumber should be(BigInteger.ONE)
      newMs.certificates("timingOut").chain.head.getSerialNumber should be(new BigInteger("2"))
    }

    "should not update the keystore if the refresh has failed" in {

      val provider : CertificateProvider = (_ : Option[CertificateHolder], _ : CommonNameProvider) =>
        Failure(new Exception("Boom"))

      val timingOut : CertificateHolder = newRootCertificate("timingOut", days = validDays / 2)
      val stillValid : CertificateHolder = newRootCertificate("stillValid", days = validDays * 2)

      val ms : MemoryKeystore = MemoryKeystore(Map("timingOut" -> timingOut, "stillValid" -> stillValid))

      val certCfgs : List[CertificateConfig] = List(
        certCfg.copy(alias = "timingOut"),
        certCfg.copy(alias = "stillValid")
      )

      val newMs = ms.refreshCertificates(certCfgs, Map("selfsigned" -> provider)).get

      newMs.certificates should have size 2
      newMs.changedAliases should be(List.empty)

      newMs.certificates("stillValid").chain.head.getSerialNumber should be(BigInteger.ONE)
      newMs.certificates("timingOut").chain.head.getSerialNumber should be(BigInteger.ONE)
    }

    "should allow to look up a certificate by the subjectPrincipal" in {

      val c1 : CertificateHolder = newRootCertificate("cert1", days = validDays)
      val c2 : CertificateHolder = newRootCertificate("cert2", days = validDays)

      val ms : MemoryKeystore = MemoryKeystore(Map("cert1" -> c1, "cert2" -> c2))

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
