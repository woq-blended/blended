package blended.mgmt.base

import scala.collection.immutable

case class RemoteContainerState(containerInfo: ContainerInfo, outstandingUpdateActions: immutable.Seq[UpdateAction])
