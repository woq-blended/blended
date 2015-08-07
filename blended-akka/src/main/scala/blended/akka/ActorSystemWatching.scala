package blended.akka

import akka.actor.{ActorRef, Props}
import blended.akka.internal.ActorSystemCapsule
import blended.akka.protocol.BundleActorStarted
import domino.DominoImplicits
import domino.capsule.CapsuleContext
import org.osgi.framework.BundleContext
import akka.actor.ActorSystem
import domino.capsule.Capsule
import org.slf4j.LoggerFactory

trait ActorSystemWatching extends DominoImplicits {

  private[this] val log = LoggerFactory.getLogger(classOf[ActorSystemWatching])

  /** Dependency */
  protected def capsuleContext: CapsuleContext

  /** Dependency */
  protected def bundleContext: BundleContext

  def whenActorSystemAvailable(f: OSGIActorConfig => Unit): Unit = {
    val m = new ActorSystemCapsule(capsuleContext, f, bundleContext)
    capsuleContext.addCapsule(m)

  }

  def setupBundleActor(cfg: OSGIActorConfig, props: Props): ActorRef = setupBundleActor(cfg.system, props)

  def setupBundleActor(system: ActorSystem, props: Props): ActorRef = {
    val actorName = bundleContext.getBundle().getSymbolicName()
    log.debug("About to create bundle actor for bundle: {}", actorName)
    val actorRef =
      //    try {
      system.actorOf(props, actorName)
    //    } catch {
    //      case e: InvalidActorNameException =>
    //        sys.error(s"Another actor registered with the same name: [${actorName}]. Cannot register multiple bundle actors.")
    //    }
    system.eventStream.publish(BundleActorStarted(actorName))

    capsuleContext.addCapsule(new Capsule {
      override def start() {
      }
      override def stop() {
        log.debug("About to stop bundle actor for bundle: {}", actorName)
        system.stop(actorRef)
      }
    })

    actorRef
  }

}
