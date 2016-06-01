package blended.updater.remote

import scala.collection.immutable

trait ContainerStatePersistor {

  def findAllContainerStates(): immutable.Seq[ContainerState]

  def findContainerState(containerId: String): Option[ContainerState]

  def updateContainerState(containerState: ContainerState): Unit

}
