package blended.updater.remote

import java.util.Date

import blended.updater.config.{Profile, UpdateAction}

case class ContainerState(
  containerId : String,
  outstandingActions : List[UpdateAction] = List.empty,
  profiles : List[Profile] = List.empty,
  syncTimeStamp : Option[Long] = None
) {

  override def toString() : String = s"${getClass().getSimpleName()}(containerId=${containerId},outstandingActions=${outstandingActions}" +
    s",profiles=${profiles},syncTimeStamp=${syncTimeStamp.map(s => new Date(s))})"

}
