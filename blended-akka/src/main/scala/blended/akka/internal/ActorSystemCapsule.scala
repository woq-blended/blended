package blended.akka.internal

import org.osgi.framework.BundleContext
import org.slf4j.LoggerFactory

import akka.actor.ActorSystem
import blended.akka.OSGIActorConfig
import blended.domino.TypesafeConfigWatching
import domino.DominoImplicits
import domino.capsule.Capsule
import domino.capsule.CapsuleContext
import domino.capsule.CapsuleScope
import domino.service_watching.ServiceWatching

class ActorSystemCapsule(
  cCtxt: CapsuleContext,
  f: OSGIActorConfig => Unit,
  bCtxt: BundleContext) extends Capsule
    with TypesafeConfigWatching
    with ServiceWatching
    with DominoImplicits {

  private[this] val log = LoggerFactory.getLogger(classOf[ActorSystemCapsule])

  var optCapsuleScope: Option[CapsuleScope] = None

  override protected def capsuleContext: CapsuleContext = cCtxt

  override protected def bundleContext: BundleContext = bCtxt

  override def start(): Unit = {
    whenServicePresent[ActorSystem] { system =>
      whenTypesafeConfigAvailable { (cfg, idSvc) =>
        if (optCapsuleScope.isEmpty) {
          log.debug("About to start: {}", this)
          val newCapsuleScope = capsuleContext.executeWithinNewCapsuleScope {
            val actorConfig = OSGIActorConfig(bundleContext, system, cfg, idSvc)
            f(actorConfig)
          }
          optCapsuleScope = Some(newCapsuleScope)
        } else {
          log.debug("Skipping start, capsule scope already defined: {}", this)
        }
      }
    }

  }

  override def stop(): Unit = {
    log.debug("About to stop: {}", this)
    optCapsuleScope.foreach(_.stop())
    optCapsuleScope = None
  }

  log.debug("Constructed: {}", this)

}
