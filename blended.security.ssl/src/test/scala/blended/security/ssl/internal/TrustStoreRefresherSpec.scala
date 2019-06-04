package blended.security.ssl.internal

import java.io.File

import blended.security.ssl.{CertificateRequestBuilder, CertificateSigner, MemoryKeystore, SecurityTestSupport}
import blended.testsupport.BlendedTestSupport
import blended.testsupport.scalatest.LoggingFreeSpec
import org.scalatest.Matchers

class TrustStoreRefresherSpec extends LoggingFreeSpec
  with Matchers
  with SecurityTestSupport
  with CertificateRequestBuilder
  with CertificateSigner {

  "The truststore refresher" - {

    val pwd : String = "trust"
    val ms : MemoryKeystore = MemoryKeystore(Map("root" -> createRootCertificate().get.copy(changed = true)))

    "not update anything if the truststore properties are not set" in {
      System.clearProperty(SslContextProvider.propTrustStorePwd)
      System.clearProperty(SslContextProvider.propTrustStore)

      val trustStore : Option[MemoryKeystore] = new TrustStoreRefresher(ms).refreshTruststore().get

      trustStore should be(empty)
    }

    "update the truststore with all missing root certificates from a given key store" in {

      val f : File = new File(BlendedTestSupport.projectTestOutput, "trust.jks")
      f.delete()

      System.setProperty(SslContextProvider.propTrustStorePwd, pwd)
      System.setProperty(SslContextProvider.propTrustStore, f.getAbsolutePath())

      val trustStore : Option[MemoryKeystore] = new TrustStoreRefresher(ms).refreshTruststore().get
      trustStore should be(defined)
      trustStore.get.certificates should have size (1)

      val updated : MemoryKeystore = new JavaKeystore(f, pwd.toCharArray(), None).loadKeyStore().get
      updated.certificates should have size (1)
    }

    "do not update the truststore if all root certificates from a given keystore already exist" in {
      val f : File = new File(BlendedTestSupport.projectTestOutput, "trust.jks")
      f.delete()

      val jks : JavaKeystore = new JavaKeystore(f, pwd.toCharArray(), None)
      jks.saveKeyStore(ms)

      System.setProperty(SslContextProvider.propTrustStorePwd, pwd)
      System.setProperty(SslContextProvider.propTrustStore, f.getAbsolutePath())

      val trustStore : Option[MemoryKeystore] = new TrustStoreRefresher(ms).refreshTruststore().get
      trustStore should be(defined)
      trustStore.get.certificates should have size (1)

      val updated : MemoryKeystore = new JavaKeystore(f, pwd.toCharArray(), None).loadKeyStore().get
      updated.certificates should have size (1)
    }
  }

}
