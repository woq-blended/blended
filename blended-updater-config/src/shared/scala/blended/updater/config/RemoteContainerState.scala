package blended.updater.config

import scala.collection.immutable

case class RemoteContainerState(containerInfo: ContainerInfo, outstandingUpdateActions: immutable.Seq[UpdateAction])
