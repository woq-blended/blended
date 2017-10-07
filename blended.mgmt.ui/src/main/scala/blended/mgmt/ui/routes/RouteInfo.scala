package blended.mgmt.ui.routes

import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.VdomElement

case class RouteInfo[T] (
  name : String,
  routerPath : Path,
  loginRequired : Boolean = true,

  content : NavigationInfo[T] => VdomElement
)