package blended.scep.internal

import javax.security.auth.x500.X500Principal

import org.slf4j.LoggerFactory

object ScepTestClient {

  private[this] val log = LoggerFactory.getLogger(classOf[ScepTestClient])

  def main(args: Array[String]) : Unit = {

    log.info("Starting Scep Test Client ...")

    val cfg = ScepConfig(
      url = "http://iqscep01:8080/pgwy/scep/sib",
      profile = None,

      /* for KL:
        - CN = phys. HostName
        - 1. SAN = phys. HostName
        - 2. SAN = log. HostName
        - O = Schwarz IT GmbH & Co. KG
        - C aus hostname

        CN=de4711.lnxprx01.4711.de.kaufland,
        SAN=cachea.4711.de.kaufland
      */
      requester = new X500Principal("CN=myserver, O=Kaufland Stiftung & Co. KG, C=DE"),
      subject = new X500Principal("CN=myserver, O=Kaufland Stiftung & Co. KG, C=DE")
    )

    new ScepEnroller(cfg).enroll()

    log.info("Scep Test Client finished ...")

  }

}

class ScepTestClient
