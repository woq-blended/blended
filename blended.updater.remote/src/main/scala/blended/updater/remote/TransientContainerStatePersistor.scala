package blended.updater.remote

class TransientContainerStatePersistor extends ContainerStatePersistor {

  private[this] var state: List[ContainerState] = List.empty

  def findContainerState(containerId: String): Option[ContainerState] = {
    state.find(s => s.containerId == containerId)
  }

  def updateContainerState(containerState: ContainerState): Unit = {
    state = containerState :: state.filter(_.containerId != containerState.containerId)
  }

  def findAllContainerStates(): List[ContainerState] = state
}
