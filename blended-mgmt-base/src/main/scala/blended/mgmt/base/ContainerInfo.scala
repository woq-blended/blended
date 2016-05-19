package blended.mgmt.base

import scala.collection.immutable

case class ContainerInfo(containerId: String, properties: Map[String, String], serviceInfos: immutable.Seq[ServiceInfo]) {
  
  override def toString(): String = s"${getClass().getSimpleName()}(containerId=${containerId},properties=${properties},serviceInfos=${serviceInfos})"
  
  //  // TODO: implement serviceInfos persistence
  //  override def persistenceProperties: PersistenceProperties = {
  //    var builder =
  //      new mutable.MapBuilder[String, PersistenceProperty[_], mutable.Map[String, PersistenceProperty[_]]](mutable.Map.empty)
  //
  //    builder += (DataObject.PROP_UUID -> objectId)
  //    properties.foreach { case (k, v) => builder += (k -> PersistenceProperty[String](v)) }
  //    (persistenceType, builder.result().toMap)
  //  }
}

case class ContainerRegistryResponseOK(id: String, actions: immutable.Seq[UpdateAction] = immutable.Seq())

case class RemoteContainerState(containerInfo: ContainerInfo, outstandingUpdateActions: immutable.Seq[UpdateAction])
