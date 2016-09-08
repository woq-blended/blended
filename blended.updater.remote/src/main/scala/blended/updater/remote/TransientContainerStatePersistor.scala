package blended.updater.remote

import scala.collection.immutable


class TransientContainerStatePersistor extends ContainerStatePersistor {

  private[this] var state: immutable.Set[ContainerState] = Set()

  def findContainerState(containerId: String): Option[ContainerState] = {
    state.find(s => s.containerId == containerId)
  }

  def updateContainerState(containerState: ContainerState): Unit = {
    state = state.filter(_.containerId != containerState.containerId) + containerState
  }

  def findAllContainerStates(): immutable.Seq[ContainerState] = {
    state.to[immutable.Seq]
  }
}
