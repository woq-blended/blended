package blended.akka

import blended.akka.internal.ActorSystemCapsule
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
}
