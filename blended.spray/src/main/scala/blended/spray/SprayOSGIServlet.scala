package blended.spray

import javax.servlet.ServletConfig

import akka.actor.{ ActorRef, ActorRefFactory, Props }
import akka.event.Logging
import akka.spray.RefUtils
import akka.util.Timeout
import blended.akka.{ ActorSystemWatching, OSGIActorConfig }
import domino.capsule.{ CapsuleContext, SimpleDynamicCapsuleContext }
import domino.service_watching.ServiceWatching
import org.osgi.framework.BundleContext
import org.slf4j.LoggerFactory
import spray.http.Uri.Path
import spray.servlet.{ ConnectorSettings, Servlet30ConnectorServlet }

import scala.concurrent.Await
import scala.concurrent.duration._
import domino.capsule.CapsuleConvenience

abstract class SprayOSGIServlet
    extends Servlet30ConnectorServlet
    with ActorSystemWatching
    with ServiceWatching
    with CapsuleConvenience {
  this: BlendedHttpRoute =>

  private[this] val sLog = LoggerFactory.getLogger(classOf[SprayOSGIServlet])
  private[this] var refFactory: Option[ActorRefFactory] = None
  private[this] var osgiActorCfg: Option[OSGIActorConfig] = None

  def actorConfig: OSGIActorConfig = osgiActorCfg match {
    case None => throw new Exception(s"OSGI Actor Config for [$bundleSymbolicName] accessed in wrong context ")
    case Some(cfg) => cfg
  }

  def servletConfig: ServletConfig = getServletConfig()

  def bundleSymbolicName = bundleContext.getBundle().getSymbolicName()

  /** Dependency */
  override protected def capsuleContext: CapsuleContext = new SimpleDynamicCapsuleContext()

  /** Dependency */
  override protected def bundleContext: BundleContext = {

    val sCtxt = servletConfig.getServletContext()
    val obj = Option(sCtxt.getAttribute("osgi-bundlecontext"))

    obj match {
      case None => throw new Exception("Attribute [osgi-bundlecontext] unefined in servlet context.")
      case Some(bc) if bc.isInstanceOf[BundleContext] => bc.asInstanceOf[BundleContext]
      case Some(o) => throw new Exception(s"[${o.toString()}] is not of class BundleContext")
    }
  }

  override implicit def actorRefFactory: ActorRefFactory = refFactory match {
    case None => throw new Exception("Actor reference factory called without Akka context")
    case Some(f) => f
  }

  def contextPath: String =
    Option(bundleContext.getBundle().getHeaders().get("Web-ContextPath")).getOrElse(bundleContext.getBundle().getSymbolicName())

  def props(route: BlendedHttpRoute): Props =
    BlendedHttpActor.props(actorConfig, this, contextPath)

  def createServletActor(): Unit =
    createServletActor(props(this))

  def createServletActor(props: Props): ActorRef = {
    sLog.debug("About to create servlet actor with props: {}", props)
    implicit val timeout = Timeout(1.second)

    system = actorConfig.system
    log = Logging(system, this.getClass)

    val symbolicName = actorConfig.bundleContext.getBundle().getSymbolicName()
    val bundleConfig = actorConfig.system.settings.config.withValue(symbolicName, actorConfig.system.settings.config.root())

    implicit val servletSettings = ConnectorSettings(bundleConfig).copy(rootPath = Path(s"/$contextPath"))
    val actor = actorConfig.system.actorOf(props)

    serviceActor = actor
    settings = servletSettings

    if (Option(system).isEmpty) throw new Exception("No ActorSystem configured")
    if (Option(serviceActor).isEmpty) throw new Exception("No ServiceActor configured")
    if (Option(settings).isEmpty) throw new Exception("No ConnectorSettings configured")
    if (!RefUtils.isLocal(serviceActor)) throw new Exception("The serviceActor must live in the same JVM as the Servlet30ConnectorServlet")

    timeoutHandler = if (settings.timeoutHandler.isEmpty)
      serviceActor
    else
      Await.result(system.actorSelection(settings.timeoutHandler).resolveOne(), timeout.duration)

    if (!RefUtils.isLocal(timeoutHandler)) throw new Exception("The timeoutHandler must live in the same JVM as the Servlet30ConnectorServlet")
    log.info(s"Initialized Servlet API 3.0 (OSGi) <=> Spray Connector for [$symbolicName]")

    serviceActor
  }

  def stopServletActor(): Unit = {
    actorConfig.system.stop(serviceActor)
  }

  def startSpray(): Unit = {
    createServletActor()
  }

  def stopSpray(): Unit = {
    stopServletActor()
  }

  override def init(): Unit = {
    sLog.info(s"About to initialise SprayOsgiServlet [${servletConfig.getServletName()}]")

    whenActorSystemAvailable { cfg =>
      osgiActorCfg = Some(cfg)
      refFactory = Some(cfg.system)
      startSpray()

      onStop {
        stopSpray()
      }
    }
  }
}
