package blended.updater.remote

import java.util.Date

import blended.mgmt.base.UpdateAction

import scala.collection.immutable

case class ContainerState(
  containerId: String,
  outstandingActions: immutable.Seq[UpdateAction] = immutable.Seq(),
  activeProfile: Option[String] = None,
  validProfiles: immutable.Seq[String] = immutable.Seq(),
  invalidProfiles: immutable.Seq[String] = immutable.Seq(),
  syncTimeStamp: Option[Long] = None) {

  override def toString(): String = s"${getClass().getSimpleName()}(containerId=${containerId},outstandingActions=${outstandingActions}" +
    s",activeProfile=${activeProfile},validProfiles=${validProfiles},invalidProfiles=${invalidProfiles},syncTimeStamp=${syncTimeStamp.map(s => new Date(s))})"

}
