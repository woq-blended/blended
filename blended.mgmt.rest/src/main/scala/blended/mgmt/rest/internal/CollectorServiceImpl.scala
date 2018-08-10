package blended.mgmt.rest.internal

import scala.collection.immutable

import akka.event.EventStream
import blended.prickle.akka.http.PrickleSupport
import blended.security.akka.http.JAASSecurityDirectives
import blended.updater.config._
import blended.updater.remote.RemoteUpdater
import blended.util.logging.Logger

class CollectorServiceImpl(
  updater: RemoteUpdater,
  remoteContainerStatePersistor: RemoteContainerStatePersistor,
  override val version: String
)
  extends CollectorService
  with JAASSecurityDirectives
  with PrickleSupport {

  private[this] lazy val log = Logger[CollectorServiceImpl]

  private[this] var eventStream: Option[EventStream] = None

  def setEventStream(eventStream: Option[EventStream]): Unit = this.eventStream = eventStream

  override def processContainerInfo(info: ContainerInfo): ContainerRegistryResponseOK = {
    log.debug(s"Processing container info: ${info}")

    eventStream.foreach { _.publish(UpdateContainerInfo(info)) }

    // next call has side-effect
    val updated = updater.updateContainerState(info)
    // this we now have updated outstanding actions
    val actions = updated.outstandingActions

    val state = RemoteContainerState(info, actions)
    remoteContainerStatePersistor.updateState(state)

    ContainerRegistryResponseOK(info.containerId, actions)
  }

  override def getCurrentState(): immutable.Seq[RemoteContainerState] = {
    val states = remoteContainerStatePersistor.findAll()
    log.debug(s"About to send state: ${states}")
    states
  }

  override def registerRuntimeConfig(rc: RuntimeConfig): Unit = updater.registerRuntimeConfig(rc)

  override def registerOverlayConfig(oc: OverlayConfig): Unit = updater.registerOverlayConfig(oc)

  override def getRuntimeConfigs(): immutable.Seq[RuntimeConfig] = updater.getRuntimeConfigs()

  override def getOverlayConfigs(): immutable.Seq[OverlayConfig] = updater.getOverlayConfigs()

  override def addUpdateAction(containerId: String, updateAction: UpdateAction): Unit = updater.addAction(containerId, updateAction)

}
