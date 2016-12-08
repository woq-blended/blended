package blended.updater.remote

import blended.updater.config._
import org.slf4j.LoggerFactory

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
        actions ++ List(action)
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
      case ActivateProfile(n, v, o) =>
        !containerProfiles.exists(p =>
          p.name == n &&
            p.version == v &&
            p.overlays.exists(po =>
              po.state == OverlayState.Active &&
                po.overlays.toSet == o.toSet
            )
        )
      case StageProfile(n, v, oc) =>
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

  def getContainerActions(containerId: ContainerId): List[UpdateAction] =
    getContainerState(containerId).map(_.outstandingActions).getOrElse(List.empty)

  def getContainerIds(): List[ContainerId] = containerStatePersistor.findAllContainerStates().map(_.containerId)

  def registerRuntimeConfig(runtimeConfig: RuntimeConfig): Unit = runtimeConfigPersistor.persistRuntimeConfig(runtimeConfig)

  def getRuntimeConfigs(): List[RuntimeConfig] = runtimeConfigPersistor.findRuntimeConfigs()

  def getOverlayConfigs(): List[OverlayConfig] = overlayConfigPersistor.findOverlayConfigs()

  def registerOverlayConfig(overlayConfig: OverlayConfig): Unit = overlayConfigPersistor.persistOverlayConfig(overlayConfig)

}

















