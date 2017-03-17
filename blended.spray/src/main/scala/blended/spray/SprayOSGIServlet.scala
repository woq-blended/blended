package blended.spray

import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.event.Logging
import akka.spray.RefUtils
import blended.akka.{ActorSystemWatching, OSGIActorConfig}
import domino.capsule.{CapsuleContext, SimpleDynamicCapsuleContext}
import domino.service_watching.ServiceWatching
import org.osgi.framework.BundleContext
import org.slf4j.LoggerFactory
import spray.http.Uri.Path
import spray.servlet.{ConnectorSettings, Servlet30ConnectorServlet}

abstract class SprayOSGIServlet extends Servlet30ConnectorServlet with ActorSystemWatching with ServiceWatching { this: BlendedHttpRoute =>

  private[this] val sLog = LoggerFactory.getLogger(classOf[SprayOSGIServlet])
  private[this] var refFactory : Option[ActorRefFactory] = None
  private[this] var osgiActorCfg : Option[OSGIActorConfig] = None

  def actorConfig : OSGIActorConfig = osgiActorCfg match {
    case None => throw new Exception(s"OSGI Actor Config for [$bundleSymbolicName] eccessed in wwrong context ")
    case Some(cfg) => cfg
  }

  def servletConfig = getServletConfig()

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

  def contextPath : String =
    Option(bundleContext.getBundle().getHeaders().get("Web-ContextPath")).getOrElse(bundleContext.getBundle().getSymbolicName())

  def props(route: BlendedHttpRoute) : Props =
    BlendedHttpActor.props(actorConfig, this, contextPath)

  def createServletActor() : Unit =
    createServletActor(props(this))

  def createServletActor(props : Props): ActorRef = {

    system = actorConfig.system
    log = Logging(system, this.getClass)

    val symbolicName = actorConfig.bundleContext.getBundle().getSymbolicName()
    log.info(s"Initialising Spray actor for [${symbolicName}], using servlet context path [$contextPath]")

    val bundleConfig = actorConfig.system.settings.config.withValue(symbolicName, actorConfig.system.settings.config.root())

    implicit val servletSettings = ConnectorSettings(bundleConfig).copy(rootPath = Path(s"/$contextPath"))
    val actor = actorConfig.system.actorOf(props)

    serviceActor = actor
    settings = servletSettings

    require(Option(system) != None, "No ActorSystem configured")
    require(Option(serviceActor) != None, "No ServiceActor configured")
    require(Option(settings) != None, "No ConnectorSettings configured")
    require(RefUtils.isLocal(serviceActor), "The serviceActor must live in the same JVM as the Servlet30ConnectorServlet")
    timeoutHandler = if (settings.timeoutHandler.isEmpty) serviceActor else system.actorFor(settings.timeoutHandler)
    require(RefUtils.isLocal(timeoutHandler), "The timeoutHandler must live in the same JVM as the Servlet30ConnectorServlet")
    log.info(s"Initialized Servlet API 3.0 (OSGi) <=> Spray Connector for [$symbolicName]")

    serviceActor
  }

  def startSpray(cfg: OSGIActorConfig): Unit = {

    osgiActorCfg = Some(cfg)
    createServletActor()
  }

  override def init(): Unit = {
    sLog.info(s"About to initialise SprayOsgiServlet [${servletConfig.getServletName()}]")

    whenActorSystemAvailable { cfg =>
      refFactory = Some(cfg.system)
      startSpray(cfg)
    }
  }
}
