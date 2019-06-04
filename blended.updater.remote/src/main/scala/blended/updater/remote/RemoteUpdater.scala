package blended.updater.remote

import blended.updater.config._
import blended.util.logging.Logger

class RemoteUpdater(
  runtimeConfigPersistor : RuntimeConfigPersistor,
  containerStatePersistor : ContainerStatePersistor,
  overlayConfigPersistor : OverlayConfigPersistor
) {

  private[this] val log = Logger[RemoteUpdater]

  type ContainerId = String

  // TODO: review: isn't this redundant with updateContainerState method?
  def addAction(containerId : ContainerId, action : UpdateAction) : Unit = {
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
      // we ignore the ID in the existence check
      if (!actions.exists(_.withId("") == action.withId(""))) {
        actions ++ List(action)
      } else {
        log.debug(s"Ignoring action with ID [${action.id}]. A same action was already scheduled")
        actions
      }
    val newState = state.copy(outstandingActions = newActions)
    log.debug(s"New container state [${newState}] with [${newState.outstandingActions.size}] outstanding actions")
    containerStatePersistor.updateContainerState(newState)
  }

  def updateContainerState(containerInfo : ContainerInfo) : ContainerState = {
    // Logic:
    // - find previous state and extract it's profiles
    // - analyze outstanding actions (filter those with missing dependencies or those known as applied)
    // - persist filtered actions as new state

    log.debug(s"About to analyze update properties from container info for container ID [${containerInfo.containerId}]")
    log.trace(s"ContainerInfo: [${containerInfo}]")

    val timeStamp = System.currentTimeMillis()
    val persistedState = containerStatePersistor.findContainerState(containerInfo.containerId).getOrElse(ContainerState(containerId = containerInfo.containerId))

    val containerProfiles = containerInfo.profiles

    val newUpdateActions = persistedState.outstandingActions
      // remove those marked as applied
      .filterNot(a => containerInfo.appliedUpdateActionIds.contains(a.id))
      // filter some inconsistent actions
      .filter {
        // TODO: why do we filter here? Performance?
        // TODO: support for overlays
        case ActivateProfile(id, n, v, o) =>
          // exclude already active activate request
          // FIXME: is this correct, e.g. if a previous action activates another profile?
          !containerProfiles.exists(p =>
            p.name == n &&
              p.version == v &&
              p.overlaySet.overlays == o &&
              p.state == OverlayState.Active)
        case StageProfile(id, n, v, oc) =>
          // exclude already staged stage request
          !containerProfiles.exists(p =>
            p.name == n &&
              p.version == v &&
              p.overlaySet.overlays == oc &&
              Set(OverlayState.Valid, OverlayState.Invalid, OverlayState.Active).exists(_ == p.overlaySet.state))
        case _ => true
      }
    val diff = newUpdateActions.size - persistedState.outstandingActions.size
    if (diff < 0) {
      log.debug(s"Removed ${-diff} actions: ${persistedState.outstandingActions.filterNot(a => newUpdateActions.contains(a))}")
    }
    if (diff > 0) {
      log.debug(s"Added ${diff} actions: ${newUpdateActions.filterNot(a => persistedState.outstandingActions.contains(a))}")
    }

    val newState = persistedState.copy(
      profiles = containerProfiles,
      outstandingActions = newUpdateActions,
      syncTimeStamp = Some(timeStamp)
    )
    containerStatePersistor.updateContainerState(newState)
    newState
  }

  def getContainerState(containerId : ContainerId) : Option[ContainerState] =
    containerStatePersistor.findContainerState(containerId)

  def getContainerActions(containerId : ContainerId) : List[UpdateAction] =
    getContainerState(containerId).map(_.outstandingActions).getOrElse(List.empty)

  def getContainerIds() : List[ContainerId] = containerStatePersistor.findAllContainerStates().map(_.containerId)

  def registerRuntimeConfig(runtimeConfig : RuntimeConfig) : Unit = runtimeConfigPersistor.persistRuntimeConfig(runtimeConfig)

  def getRuntimeConfigs() : List[RuntimeConfig] = runtimeConfigPersistor.findRuntimeConfigs()

  def getOverlayConfigs() : List[OverlayConfig] = overlayConfigPersistor.findOverlayConfigs()

  def registerOverlayConfig(overlayConfig : OverlayConfig) : Unit = overlayConfigPersistor.persistOverlayConfig(overlayConfig)

}

