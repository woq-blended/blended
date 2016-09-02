package blended.mgmt.base

import scala.collection.immutable

case class ContainerInfo(
    containerId: String,
    properties: Map[String, String],
    serviceInfos: immutable.Seq[ServiceInfo],
    profiles: immutable.Seq[Profile]) {

  override def toString(): String = s"${getClass().getSimpleName()}(containerId=${containerId},properties=${properties},serviceInfos=${serviceInfos},profiles=${profiles})"

}


