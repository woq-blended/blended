package blended.updater.remote

import blended.updater.config._
import blended.util.logging.Logger

class RemoteUpdater(
  runtimeConfigPersistor: RuntimeConfigPersistor,
  containerStatePersistor: ContainerStatePersistor,
  overlayConfigPersistor: OverlayConfigPersistor
) {

  private[this] val log = Logger[RemoteUpdater]

  type ContainerId = String

  // TODO: review: isn't this redundant with updateContainerState method?
  def addAction(containerId: ContainerId, action: UpdateAction): Unit = {
    // Logic:
    // - find previous state
    // - add new actions
    // - calc new state
    // - persist new state

    log.debug(s"About to add action [${action}] for container [${containerId}]")
    val state = containerStatePersistor.findContainerState(containerId).getOrElse(ContainerState(containerId = containerId))
    val actions = state.outstandingActions
    log.debug(s"Found [${actions.size}] old outstanding actions for container [${containerId}]")
    val newActions =
      if (!actions.exists(_ == action)) {
        actions ++ List(action)
      } else {
        log.debug("A same action was already scheduled")
        actions
      }
    val newState = state.copy(outstandingActions = newActions)
    log.debug(s"New container state [${newState}] with [${newState.outstandingActions.size}] outstanding actions")
    containerStatePersistor.updateContainerState(newState)
  }

  def updateContainerState(containerInfo: ContainerInfo): ContainerState = {
    // Logic:
    // - find previous state and extract it's profiles
    // - analyze outstanding actions (filter those with missing dependencies)
    // - persist filtered actions as new state

    log.debug(s"About to analyze update properties from container info for container ID [${containerInfo.containerId}]")
    log.trace(s"ContainerInfo: [${containerInfo}]")

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
                po.overlays.toSet == o.toSet))
      case StageProfile(n, v, oc) =>
        !containerProfiles.exists(p =>
          p.name == n &&
            p.version == v &&
            p.overlays.exists(po =>
              Set(OverlayState.Valid, OverlayState.Invalid, OverlayState.Active).exists(_ == po.state) &&
                po.overlays.toSet == oc.toSet))
      case _ => true
    }
    val diff = newUpdateActions.size - state.outstandingActions.size
    if(diff < 0) {
      log.debug(s"Removed ${-diff} actions: ${state.outstandingActions.filterNot(a => newUpdateActions.contains(a))}")
    }
    if(diff > 0) {
      log.debug(s"Added ${diff} actions: ${newUpdateActions.filterNot(a => state.outstandingActions.contains(a))}")
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

