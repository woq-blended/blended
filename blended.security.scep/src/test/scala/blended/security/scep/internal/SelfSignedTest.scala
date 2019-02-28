package blended.security.scep.internal

import java.io.File

import blended.security.ssl._
import blended.security.ssl.internal.{JavaKeystore, MemoryKeystore}

import scala.util.Try

object SelfSignedTest {

  private val selfSignedCfg : SelfSignedConfig = SelfSignedConfig(
    commonNameProvider = new CommonNameProvider {
      override def commonName(): Try[String] = Try { "CN=cachea.9999.cc.kaufland, O=Schwarz IT GmbH & Co. KG, C=CC" }
      //override def commonName(): Try[String] = Try { "CN=cc9999lnxprx01.9999.cc.kaufland, O=Schwarz IT GmbH & Co. KG, C=CC" }
      override def alternativeNames(): Try[List[String]] = Try { List("cc9999lnxprx01.9999.cc.kaufland", "cachea.9999.cc.kaufland") }
    },
    keyStrength = 2048,
    sigAlg = "SHA256withRSA",
    validDays = 1
  )

  private val provider : CertificateProvider = new SelfSignedCertificateProvider(selfSignedCfg)

  def main(args: Array[String]) : Unit = {

    val keystore = new JavaKeystore(new File("/tmp/keystore"), "test".toCharArray, Some("test".toCharArray))
    val memStore = new MemoryKeystore(Map.empty)

    val cert : CertificateHolder = provider.refreshCertificate(None, selfSignedCfg.commonNameProvider).get.copy(changed = true)
    println(cert.dump)

    keystore.saveKeyStore(memStore.update("cert", cert).get)

  }

}
