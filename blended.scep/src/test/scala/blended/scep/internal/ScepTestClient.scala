package blended.scep.internal

import javax.security.auth.x500.X500Principal

import org.slf4j.LoggerFactory

object ScepTestClient {

  private[this] val log = LoggerFactory.getLogger(classOf[ScepTestClient])

  def main(args: Array[String]) : Unit = {

    log.info("Starting Scep Test Client ...")

    val cfg = ScepConfig(
      url = "http://localhost:8080/scep",
      profile = None,
      requester = new X500Principal("CN=andreas, O=WOQ, C=DE"),
      subject = new X500Principal("CN=myserver, O=WOQ, C=DE")
    )

    new ScepEnroller(cfg).enroll()

    log.info("Scep Test Client finished ...")

  }

}

class ScepTestClient
