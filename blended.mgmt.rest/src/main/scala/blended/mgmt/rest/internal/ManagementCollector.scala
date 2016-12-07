package blended.mgmt.rest.internal

import javax.servlet.Servlet

import akka.actor.{Actor, ActorRefFactory, Props}
import blended.akka.{OSGIActor, OSGIActorConfig, ProductionEventSource}
import blended.spray.{SprayOSGIBridge, SprayOSGIServlet}
import blended.updater.config._
import blended.updater.remote.RemoteUpdater
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import spray.http.Uri.Path
import spray.routing._
import spray.servlet.ConnectorSettings
import spray.util.LoggingContext

import scala.collection.immutable

import blended.spray.SprayPrickleSupport

case class ManagementCollectorConfig(
  servletSettings: ConnectorSettings,
  routingSettings: RoutingSettings,
  contextPath: String,
  remoteUpdater: RemoteUpdater) {

  override def toString(): String = s"${getClass.getSimpleName}(servletSettings=${servletSettings},routingSettings=${routingSettings},contextPath=${contextPath},remoteUpdater=${remoteUpdater})"
}

object ManagementCollectorConfig {
  def apply(config: Config, contextPath: String, remoteUpdater: RemoteUpdater): ManagementCollectorConfig = ManagementCollectorConfig(
    servletSettings = ConnectorSettings(config).copy(rootPath = Path(s"/${contextPath}")),
    routingSettings = RoutingSettings(config),
    contextPath = contextPath,
    remoteUpdater = remoteUpdater
  )
}

object ManagementCollector {

  def props(cfg: OSGIActorConfig, config: ManagementCollectorConfig): Props = Props(new ManagementCollector(cfg, config))
}

class ManagementCollector(cfg: OSGIActorConfig, config: ManagementCollectorConfig)
  extends OSGIActor(cfg)
    with CollectorService
    with ProductionEventSource
    with SprayPrickleSupport {

  type ContainerId = String

  private[this] val mylog = LoggerFactory.getLogger(classOf[ManagementCollector])

  private[this] var containerStates: Map[ContainerId, RemoteContainerState] = Map()

  // Required by CollectorService
  override implicit def actorRefFactory: ActorRefFactory = context

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    mylog.debug("Processing container info: {}", info)
    sendEvent(UpdateContainerInfo(info))
    val updater = config.remoteUpdater
    val newActions = updater.getContainerActions(info.containerId)
    containerStates += info.containerId -> RemoteContainerState(info, newActions)
    ContainerRegistryResponseOK(info.containerId, newActions)
  }

  override def preStart(): Unit = {
    val servletSettings = config.servletSettings
    implicit val routingSettings = config.routingSettings
    implicit val routeLogger = LoggingContext.fromAdapter(log)
    implicit val exceptionHandler = ExceptionHandler.default
    implicit val rejectionHandler = RejectionHandler.Default

    val actorSys = context.system
    val routingActor = self

    val servlet = new SprayOSGIServlet with SprayOSGIBridge {
      override def routeActor = routingActor

      override def connectorSettings = servletSettings

      override def actorSystem = actorSys
    }

    servlet.providesService[Servlet](
      "urlPatterns" -> "/",
      "Webapp-Context" -> config.contextPath,
      "Web-ContextPath" -> s"/${config.contextPath}"
    )

    context.become(runRoute(collectorRoute ~ infoRoute ~ versionRoute ~ runtimeConfigRoute ~ overlayConfigRoute))
  }

  def receive: Receive = Actor.emptyBehavior

  override def getCurrentState(): immutable.Seq[RemoteContainerState] = {
    mylog.debug("About to send state: {}", containerStates)
    containerStates.values.toList
  }

  override def version: String = cfg.bundleContext.getBundle().getVersion().toString()

  override def registerRuntimeConfig(rc: RuntimeConfig): Unit = config.remoteUpdater.registerRuntimeConfig(rc)

  override def registerOverlayConfig(oc: OverlayConfig): Unit = config.remoteUpdater.registerOverlayConfig(oc)

  override def getRuntimeConfigs(): immutable.Seq[RuntimeConfig] = config.remoteUpdater.getRuntimeConfigs()

  override def getOverlayConfigs(): immutable.Seq[OverlayConfig] = config.remoteUpdater.getOverlayConfigs()
}
