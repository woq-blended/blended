package blended.akka.internal

import akka.actor.ActorSystem
import blended.akka.OSGIActorConfig
import blended.domino.TypesafeConfigWatching
import blended.util.logging.Logger
import domino.DominoImplicits
import domino.capsule.{Capsule, CapsuleContext, CapsuleScope}
import domino.service_watching.ServiceWatching
import org.osgi.framework.BundleContext

class ActorSystemCapsule(
  cCtxt : CapsuleContext,
  f : OSGIActorConfig => Unit,
  bCtxt : BundleContext
) extends Capsule
  with TypesafeConfigWatching
  with ServiceWatching
  with DominoImplicits {

  private[this] val log = Logger[ActorSystemCapsule]

  var optCapsuleScope : Option[CapsuleScope] = None

  override protected def capsuleContext : CapsuleContext = cCtxt

  override protected def bundleContext : BundleContext = bCtxt

  override def start() : Unit = {
    whenServicePresent[ActorSystem] { system =>
      whenTypesafeConfigAvailable { (cfg, idSvc) =>
        if (optCapsuleScope.isEmpty) {
          log.debug(s"About to start: ${this}")
          val newCapsuleScope = capsuleContext.executeWithinNewCapsuleScope {
            val actorConfig = OSGIActorConfig(bundleContext, system, cfg, idSvc)
            f(actorConfig)
          }
          optCapsuleScope = Some(newCapsuleScope)
        } else {
          log.debug(s"Skipping start, capsule scope already defined: ${this}")
        }
      }
    }

  }

  override def stop() : Unit = {
    log.debug(s"About to stop: ${this}")
    optCapsuleScope.foreach(_.stop())
    optCapsuleScope = None
  }

  log.debug(s"Constructed: ${this}")

  override def toString() : String = getClass().getSimpleName() + "(cCtx=" + cCtxt + ",bCtxt=" + bCtxt + ")"

}
