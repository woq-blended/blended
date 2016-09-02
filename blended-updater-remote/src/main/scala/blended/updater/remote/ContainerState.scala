package blended.updater.remote

import java.util.Date

import blended.mgmt.base.UpdateAction

import scala.collection.immutable
import blended.mgmt.base.Profile

case class ContainerState(
  containerId: String,
  outstandingActions: immutable.Seq[UpdateAction] = immutable.Seq(),
  profiles: immutable.Seq[Profile] = immutable.Seq(),
  syncTimeStamp: Option[Long] = None) {

  override def toString(): String = s"${getClass().getSimpleName()}(containerId=${containerId},outstandingActions=${outstandingActions}" +
    s",profiles=${profiles},syncTimeStamp=${syncTimeStamp.map(s => new Date(s))})"

}
