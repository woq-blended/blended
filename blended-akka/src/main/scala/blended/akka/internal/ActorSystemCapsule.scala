package blended.akka.internal

import akka.actor.ActorSystem
import blended.domino.TypesafeConfigWatching
import blended.akka.OSGIActorConfig
import domino.DominoImplicits
import domino.capsule.{CapsuleScope, CapsuleContext, Capsule}
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

  var optCapsuleScope : Option[CapsuleScope] = None

  override protected def capsuleContext: CapsuleContext = cCtxt

  override protected def bundleContext: BundleContext = bCtxt

  override def start(): Unit = {
    whenServicePresent[ActorSystem] { system =>
      whenTypesafeConfigAvailable { (cfg, idSvc) =>
        if (optCapsuleScope.isEmpty) {
          val newCapsuleScope = capsuleContext.executeWithinNewCapsuleScope {
            val actorConfig = OSGIActorConfig(bundleContext, system, cfg, idSvc)
            f(actorConfig)
          }
          optCapsuleScope = Some(newCapsuleScope)
        }
      }
    }

  }

  override def stop(): Unit = {
    optCapsuleScope.foreach(_.stop())
  }
}
