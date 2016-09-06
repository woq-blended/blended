package blended.mgmt.repo.rest.internal

import org.slf4j.LoggerFactory

import domino.DominoActivator
import blended.mgmt.repo.ArtifactRepo

class ArtifactRepoRestActivator extends DominoActivator {

  private[this] val log = LoggerFactory.getLogger(classOf[ArtifactRepoRestActivator])

  whenBundleActive {
    log.debug("About to activate bundle: {}", bundleContext.getBundle().getSymbolicName())

    // TODO: read config 
    // TODO: watch for ArtifactRepo services
    
    // TODO: when both available, setup spray accordingly
    
  }

}