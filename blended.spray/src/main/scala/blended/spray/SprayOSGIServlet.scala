package blended.spray

import javax.servlet.ServletConfig

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
  private[this] var initConfig : Option[ServletConfig] = None
  private[this] var refFactory : Option[ActorRefFactory] = None

  /** Dependency */
  override protected def capsuleContext: CapsuleContext = new SimpleDynamicCapsuleContext()

  /** Dependency */
  override protected def bundleContext: BundleContext = {

    require(initConfig.isDefined)
    val sCtxt = initConfig.get.getServletContext()
    val obj = sCtxt.getAttribute("osgi-bundlecontext")
    require(Option(obj).isDefined)
    obj.asInstanceOf[BundleContext]
  }

  override implicit def actorRefFactory: ActorRefFactory = refFactory match {
    case None => throw new Exception("Actor reference factory called without Akka context")
    case Some(f) => f
  }

  def contextPath(cfg: OSGIActorConfig) : String =
    if (cfg.config.hasPath("contextPath")) cfg.config.getString("contextPath") else initConfig.get.getServletName()

  def props(cfg: OSGIActorConfig, route: BlendedHttpRoute) : Props =
    BlendedHttpActor.props(cfg, this, contextPath(cfg))

  def createServletActor(osgiCfg: OSGIActorConfig) : Unit =
    createServletActor(osgiCfg, props(osgiCfg, this))

  def createServletActor(osgiCfg: OSGIActorConfig, props : Props): ActorRef = {

    system = osgiCfg.system
    log = Logging(system, this.getClass)

    val cPath = contextPath(osgiCfg)
    val symbolicName = osgiCfg.bundleContext.getBundle().getSymbolicName()
    log.info(s"Initialising Spray actor for [${symbolicName}], using servlet context path [$cPath]")

    val bundleConfig = osgiCfg.system.settings.config.withValue(symbolicName, osgiCfg.system.settings.config.root())

    implicit val servletSettings = ConnectorSettings(bundleConfig).copy(rootPath = Path(s"/$cPath"))
    val actor = osgiCfg.system.actorOf(props)

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
    createServletActor(cfg)
  }

  override def init(servletConfig: ServletConfig): Unit = {

    super.init()
    sLog.info(s"About to initialise SprayOsgiServlet [${servletConfig.getServletName()}]")
    initConfig = Some(servletConfig)

    whenActorSystemAvailable { cfg =>
      refFactory = Some(cfg.system)
      startSpray(cfg)
    }

  }

}
