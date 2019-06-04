package blended.security.scep.internal

import java.io.File

import blended.security.ssl.internal.JavaKeystore

import scala.util.Try
import blended.security.ssl.{CommonNameProvider, MemoryKeystore, X509CertificateInfo}
import blended.util.logging.Logger
import javax.tools.DocumentationTool.Location

object ScepTestClient {

  private[this] val log = Logger[ScepTestClient]

  private val keystore = new JavaKeystore(new File("/tmp/keystore"), "test".toCharArray, Some("test".toCharArray))

  def main(args : Array[String]) : Unit = {

    log.info("Starting Scep Test Client ...")

    val country = "de"
    val location = "0113"

    val cnProvider = new CommonNameProvider {
      //override def commonName(): Try[String] = Try { "CN=cachea.9999.cc.kaufland, O=Schwarz IT GmbH & Co. KG, C=CC" }
      override def commonName() : Try[String] = Try { s"CN=${country}${location}lnxprx02.${location}.${country}.kaufland, O=Schwarz IT GmbH & Co. KG, C=${country.toUpperCase()}" }
      override def alternativeNames() : Try[List[String]] = Try {
        List(
          s"${country}${location}lnxprx02.${location}.de.kaufland",
          s"cacheb.${location}.${country}.kaufland"
        )
      }
    }

    val scepConfig = new ScepConfig(
      url = "http://scep-t.pki.schwarz:8080/pgwy/scep/sib",
      profile = None,
      keyLength = 2048,
      csrSignAlgorithm = "SHA1withRSA",
      scepChallenge = "qXUDZTttAZDghBguVk2M"
    )

    val provider = new ScepCertificateProvider(scepConfig)

    val cert1 = provider.refreshCertificate(None, cnProvider).get.copy(changed = true)

    cert1.chain.foreach { c =>
      log.info(X509CertificateInfo(c).toString)
    }

    log.info("=" * 100)

    val cert2 = provider.refreshCertificate(Some(cert1), cnProvider).get.copy(changed = true)

    cert2.chain.foreach { c =>
      log.info(X509CertificateInfo(c).toString)
    }

    assert(cert1.keypairValid.contains(true))
    assert(cert1.keypairValid.contains(true))

    val memStore : MemoryKeystore = new MemoryKeystore(Map.empty)
      .update("initial", cert1).get
      .update("refreshed", cert2).get

    keystore.saveKeyStore(memStore)
  }
}

class ScepTestClient
