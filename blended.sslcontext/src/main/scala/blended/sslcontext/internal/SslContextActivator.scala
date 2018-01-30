package blended.sslcontext.internal

import org.log4s._
import domino.DominoActivator

class SslContextActivator extends DominoActivator {

  private[this] val log = getLogger

  whenBundleActive {
    log.info("Initialising SSL Context Bundle")

    onStop{

      log.info("Stopping SSL Context Bundle")
    }
  }

}
