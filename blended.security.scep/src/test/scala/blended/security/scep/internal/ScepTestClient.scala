package blended.security.scep.internal

import java.security.cert.X509Certificate

import blended.security.ssl.{CommonNameProvider, X509CertificateInfo}
import org.slf4j.LoggerFactory

object ScepTestClient {

  private[this] val log = LoggerFactory.getLogger(classOf[ScepTestClient])

  def main(args: Array[String]) : Unit = {

    log.info("Starting Scep Test Client ...")

//    val cfg = ScepConfig(
//      url = "http://iqscep01:8080/pgwy/scep/sib",
//      profile = None,
//
//      /* for KL:
//        - CN = phys. HostName
//        - 1. SAN = phys. HostName
//        - 2. SAN = log. HostName
//        - O = Schwarz IT GmbH & Co. KG
//        - C aus hostname
//
//        CN=de4711.lnxprx01.4711.de.kaufland,
//        SAN=cachea.4711.de.kaufland
//      */
//      requester = new X500Principal("CN=myserver, O=Kaufland Stiftung & Co. KG, C=DE"),
//      subject = new X500Principal("CN=myserver, O=Kaufland Stiftung & Co. KG, C=DE")
//    )

    val cnProvider = new CommonNameProvider {
      override def commonName(): String = "CN=myserver, O=Kaufland Stiftung & Co. KG, C=DE"
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

    cert1.chain.map(_.asInstanceOf[X509Certificate]).foreach { c =>
      log.info(X509CertificateInfo(c).toString)
    }

    log.info("=" * 100)

    val cert2 = provider.refreshCertificate(Some(cert1)).get

    cert2.chain.map(_.asInstanceOf[X509Certificate]).foreach { c =>
      log.info(X509CertificateInfo(c).toString)
    }
  }
}

class ScepTestClient
