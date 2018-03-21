package blended.akka

import blended.container.context.ContainerContext
import com.typesafe.config.Config
import org.osgi.framework.BundleContext

import scala.collection.convert.Wrappers.JPropertiesWrapper
import scala.concurrent.Future
import scala.concurrent.duration._

abstract class OSGIActor(actorConfig: OSGIActorConfig) 
  extends Actor
  with ActorLogging 
  with ServiceConsuming
  with ServiceProviding {

  private[this] implicit val timeout = new Timeout(500.millis)
  private[this] implicit val ec = context.dispatcher

  override protected def capsuleContext: CapsuleContext = new SimpleDynamicCapsuleContext()

  override protected def bundleContext: BundleContext = actorConfig.bundleContext
  
  def bundleActor(bundleName : String) : Future[ActorRef] = {
    log debug s"Trying to resolve bundle actor [$bundleName]"
    context.actorSelection(s"/user/$bundleName").resolveOne().fallbackTo(Future(context.system.deadLetters))
  }

  // Returns application.conf merged with the bundle specific config object
  protected def bundleActorConfig : Config =
    context.system.settings.config.withValue(bundleSymbolicName, actorConfig.config.root())

  val bundleSymbolicName: String = actorConfig.bundleContext.getBundle().getSymbolicName()
}
