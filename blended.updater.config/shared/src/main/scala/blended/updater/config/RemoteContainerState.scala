package blended.updater.config

case class RemoteContainerState(containerInfo: ContainerInfo, outstandingUpdateActions: List[UpdateAction])
