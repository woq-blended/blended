package blended.mgmt.rest.internal

import akka.util.Timeout
import blended.updater.config._
import blended.updater.config.json.PrickleProtocol._
import org.slf4j.LoggerFactory
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._

import scala.collection.immutable
import scala.concurrent.duration._
import blended.security.akka.http.BlendedSecurityDirectives
import blended.prickle.akka.http.PrickleSupport
import akka.http.scaladsl.server.ValidationRejection
import blended.security.akka.http.ShiroBlendedSecurityDirectives
import blended.updater.remote.RemoteUpdater
import blended.updater.remote.ContainerStatePersistor
import akka.event.EventStream
import org.apache.shiro.mgt.SecurityManager

class CollectorServiceImpl(
  securityManager: SecurityManager,
  updater: RemoteUpdater,
  remoteContainerStatePersistor: RemoteContainerStatePersistor,
  override val version: String
)
  extends CollectorService
  with ShiroBlendedSecurityDirectives
  with PrickleSupport {

  private[this] lazy val log = org.log4s.getLogger

  private[this] var eventStream: Option[EventStream] = None

  override protected def securityManager(): Option[SecurityManager] = Option(securityManager)

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
