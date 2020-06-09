package blended.updater.remote

import java.util.Date

import blended.updater.config.{ProfileRef, UpdateAction}

case class ContainerState(
    containerId: String,
    outstandingActions: List[UpdateAction] = List.empty,
    profiles: List[ProfileRef] = List.empty,
    syncTimeStamp: Option[Long] = None
) {

  override def toString(): String =
    getClass().getSimpleName() +
      "(containerId=" + containerId +
      ",outstandingActions=" + outstandingActions +
      ",profiles=" + profiles +
      ",syncTimeStamp=" + syncTimeStamp.map(s => new Date(s)) +
      ")"

}
