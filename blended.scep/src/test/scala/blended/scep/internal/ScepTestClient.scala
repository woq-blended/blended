package blended.scep.internal

import org.slf4j.LoggerFactory

object ScepTestClient {

  private[this] val log = LoggerFactory.getLogger(classOf[ScepTestClient])

  def main(args: Array[String]) : Unit = {

    log.info("Starting Scep Test Client ...")

    new ScepEnroller().enroll()

    log.info("Scep Test Client finished ...")

  }

}

class ScepTestClient
