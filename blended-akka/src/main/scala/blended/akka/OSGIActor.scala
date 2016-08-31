package blended.akka

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.util.Timeout
import com.typesafe.config.Config
import blended.container.context.ContainerContext
import domino.capsule.{SimpleDynamicCapsuleContext, CapsuleContext}
import domino.service_consuming.ServiceConsuming
import domino.service_providing.ServiceProviding
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

  protected def containerProperties : Map[String, String] = JPropertiesWrapper(actorConfig.idSvc.getProperties()).toMap

  protected def containerUUID : String = actorConfig.idSvc.getUUID()

  protected def containerContext : ContainerContext = actorConfig.idSvc.getContainerContext()
}
