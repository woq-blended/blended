package blended.mgmt.ui.routes

import japgolly.scalajs.react.extra.router.RouterCtl

case class NavigationInfo[T] (

  val current : T,
  val ctl : RouterCtl[T]

)
