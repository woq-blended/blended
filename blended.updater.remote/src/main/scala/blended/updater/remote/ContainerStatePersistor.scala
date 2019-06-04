package blended.updater.remote

trait ContainerStatePersistor {

  def findAllContainerStates() : List[ContainerState]

  def findContainerState(containerId : String) : Option[ContainerState]

  def updateContainerState(containerState : ContainerState) : Unit

}
