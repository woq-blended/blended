package blended.akka

import akka.actor.{ActorRef, Props}
import blended.akka.internal.ActorSystemCapsule
import blended.akka.protocol.BundleActorStarted
import domino.DominoImplicits
import domino.capsule.CapsuleContext
import org.osgi.framework.BundleContext

trait ActorSystemWatching extends DominoImplicits {

  /** Dependency */
  protected def capsuleContext: CapsuleContext

  /** Dependency */
  protected def bundleContext: BundleContext

  def whenActorSystemAvailable(f: OSGIActorConfig => Unit) = {
    val m = new ActorSystemCapsule(capsuleContext, f, bundleContext)
    capsuleContext.addCapsule(m)

  }

  def setupBundleActor(cfg: OSGIActorConfig, props: Props) : ActorRef = {
    val actorRef = cfg.system.actorOf(props, bundleContext.getBundle().getSymbolicName())
    cfg.system.eventStream.publish(BundleActorStarted(bundleContext.getBundle().getSymbolicName()))

    actorRef
  }

  def stopBundleActor : (OSGIActorConfig, ActorRef) => Unit = { (cfg, actor) =>
    cfg.system.stop(actor)
  }

}
