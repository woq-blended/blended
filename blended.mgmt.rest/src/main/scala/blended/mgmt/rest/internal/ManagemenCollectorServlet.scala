package blended.mgmt.rest.internal

import blended.akka.OSGIActorConfig
import blended.spray.{SprayOSGIServlet, SprayPrickleSupport}
import blended.updater.config._
import blended.updater.remote.RemoteUpdater
import org.slf4j.LoggerFactory

import scala.collection.immutable

class ManagemenCollectorServlet extends SprayOSGIServlet
  with CollectorService
  with SprayPrickleSupport {

  type ContainerId = String

  private[this] val mylog = LoggerFactory.getLogger(classOf[ManagemenCollectorServlet])

  private[this] var containerStates: Map[ContainerId, RemoteContainerState] = Map()

  private[this] var remoteUpdater : Option[RemoteUpdater] = None

  private[this] var cfg : Option[OSGIActorConfig] = None

  override def startSpray(): Unit = {

    whenServicePresent[RemoteUpdater]{ updater =>

      remoteUpdater = Some(updater)
      val actor = createServletActor()
    }
  }

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    mylog.debug("Processing container info: {}", info)

    cfg.foreach(_.system.eventStream.publish(UpdateContainerInfo(info)))

    val newActions = remoteUpdater match {
      case None =>
        mylog.warn(s"Process container info called with no remote updater available.")
        List.empty
      case Some(updater) =>
        val actions = updater.getContainerActions(info.containerId)
        containerStates += info.containerId -> RemoteContainerState(info, actions)
        actions
    }
    ContainerRegistryResponseOK(info.containerId, newActions)
  }

  override def version: String = cfg match {
    case None => "UNKNOWN"
    case Some(c) => c.bundleContext.getBundle().getVersion().toString()
  }

  override def getCurrentState(): immutable.Seq[RemoteContainerState] = {
    mylog.debug("About to send state: {}", containerStates)
    containerStates.values.toList
  }

  override def registerRuntimeConfig(rc: RuntimeConfig): Unit = remoteUpdater match {
    case None => throw new Exception("registerRuntimeConfig called with no remote updater available")
    case Some(updater) => updater.registerRuntimeConfig(rc)
  }

  override def registerOverlayConfig(oc: OverlayConfig): Unit = remoteUpdater match {
    case None => throw new Exception("registerOverlayConfig called with no remote updater available")
    case Some(updater) => updater.registerOverlayConfig(oc)
  }

  override def getRuntimeConfigs(): immutable.Seq[RuntimeConfig] = remoteUpdater match {
    case None => immutable.Seq.empty[RuntimeConfig]
    case Some(updater) => updater.getRuntimeConfigs()
  }

  override def getOverlayConfigs(): immutable.Seq[OverlayConfig] = remoteUpdater match {
    case None => immutable.Seq.empty[OverlayConfig]
    case Some(updater) => updater.getOverlayConfigs()
  }

  override def addUpdateAction(containerId: String, updateAction: UpdateAction): Unit = remoteUpdater match {
    case None => throw new Exception("addUpdateAction called with no remote updater available")
    case Some(updater) => updater.addAction(containerId, updateAction)
  }
}
