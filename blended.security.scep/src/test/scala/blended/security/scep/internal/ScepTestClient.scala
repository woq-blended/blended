package blended.security.scep.internal

import blended.security.ssl.{CommonNameProvider, X509CertificateInfo}
import org.slf4j.LoggerFactory

import scala.util.Try

object ScepTestClient {

  private[this] val log = LoggerFactory.getLogger(classOf[ScepTestClient])

  def main(args: Array[String]) : Unit = {

    log.info("Starting Scep Test Client ...")

    val cnProvider = new CommonNameProvider {
      override def commonName(): Try[String] = Try { "CN=cc9999lnxprx01.9999.cc.kaufland, O=Schwarz IT GmbH & Co. KG, C=CC" }
      override def alternativeNames(): Try[List[String]] = Try { List("cc9999lnxprx01.9999.cc.kaufland", "cachea.9999.cc.kaufland") }
    }

    val scepConfig = new ScepConfig(
      url = "http://iqscep01:8080/pgwy/scep/sib",
      cnProvider = cnProvider,
      profile = None,
      keyLength = 2048,
      csrSignAlgorithm = "SHA1withRSA",
      scepChallenge ="password"
    )

    val provider = new ScepCertificateProvider(scepConfig)

    val cert1 = provider.refreshCertificate(None).get

    cert1.chain.foreach { c =>
      log.info(X509CertificateInfo(c).toString)
    }

    log.info("=" * 100)

    val cert2 = provider.refreshCertificate(Some(cert1)).get

    cert2.chain.foreach { c =>
      log.info(X509CertificateInfo(c).toString)
    }
  }
}

class ScepTestClient
