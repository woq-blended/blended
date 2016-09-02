package blended.updater.remote

import java.io.File

import blended.mgmt.base.ContainerInfo
import blended.updater.config.ConfigWriter
import blended.updater.config.OverlayConfig
import blended.updater.config.RuntimeConfig
import com.typesafe.config.ConfigFactory
import scala.collection.immutable
import blended.mgmt.base.StageProfile
import blended.mgmt.base.UpdateAction
import blended.mgmt.base.ActivateProfile
import org.slf4j.LoggerFactory
import java.util.Date

import scala.util.Try
import blended.mgmt.base.OverlayState

class RemoteUpdater(runtimeConfigPersistor: RuntimeConfigPersistor,
    containerStatePersistor: ContainerStatePersistor, overlayConfigPersistor: OverlayConfigPersistor) {

  private[this] val log = LoggerFactory.getLogger(classOf[RemoteUpdater])

  type ContainerId = String

  def addAction(containerId: ContainerId, action: UpdateAction): Unit = {
    val state = containerStatePersistor.findContainerState(containerId).getOrElse(ContainerState(containerId = containerId))
    val actions = state.outstandingActions
    val newActions =
      if (!actions.exists {
        _ == action
      }) {
        actions ++ immutable.Seq(action)
      } else actions
    containerStatePersistor.updateContainerState(state.copy(outstandingActions = newActions))
  }

  def updateContainerState(containerInfo: ContainerInfo): ContainerState = {
    log.debug(s"About to analyze update properties from container info: ${containerInfo}")
    val timeStamp = System.currentTimeMillis()
    val state = containerStatePersistor.findContainerState(containerInfo.containerId).getOrElse(ContainerState(containerId = containerInfo.containerId))

    val containerProfiles = containerInfo.profiles

    val newUpdateActions = state.outstandingActions.filter {
      // TODO: support for overlays
      case ActivateProfile(n, v, o, _kind) =>
        !containerProfiles.exists(p =>
          p.name == n &&
            p.version == v &&
            p.overlays.exists(po =>
              po.state == OverlayState.Active &&
                po.overlays.toSet == o.toSet
            )
        )
      case StageProfile(n, v, oc, _kind) =>
        !containerProfiles.exists(p =>
          p.name == n &&
            p.version == v &&
            p.overlays.exists(po =>
              Set(OverlayState.Valid, OverlayState.Invalid, OverlayState.Active).exists(_ == po.state) &&
                po.overlays.toSet == oc.toSet
            )
        )
      case _ => true
    }

    val newState = state.copy(
      profiles = containerProfiles,
      outstandingActions = newUpdateActions,
      syncTimeStamp = Some(timeStamp)
    )
    containerStatePersistor.updateContainerState(newState)
    newState
  }

  def getContainerState(containerId: ContainerId): Option[ContainerState] =
    containerStatePersistor.findContainerState(containerId)

  def getContainerActions(containerId: ContainerId): immutable.Seq[UpdateAction] =
    getContainerState(containerId).map(_.outstandingActions).getOrElse(immutable.Seq())

  def getContainerIds(): immutable.Seq[ContainerId] = containerStatePersistor.findAllContainerStates().map(_.containerId)

  def registerRuntimeConfig(runtimeConfig: RuntimeConfig): Unit = runtimeConfigPersistor.persistRuntimeConfig(runtimeConfig)

  def getRuntimeConfigs(): immutable.Seq[RuntimeConfig] = runtimeConfigPersistor.findRuntimeConfigs()

  def getOverlayConfigs(): immutable.Seq[OverlayConfig] = overlayConfigPersistor.findOverlayConfigs()

  def registerOverlayConfig(overlayConfig: OverlayConfig): Unit = overlayConfigPersistor.persistOverlayConfig(overlayConfig)

}

















