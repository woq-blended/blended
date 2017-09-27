package blended.mgmt.ui.routes

import blended.mgmt.ui.util.I18n
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.html_<^._

trait MgmtPage {

  /** Use this to internationalize mesages. */
	val i18n = I18n()
  val routeInfo : RouteInfo[MgmtPage]

}

object MgmtPage {

  def apply(
    name : String,
    routerPath : String,
    content : NavigationInfo[MgmtPage] => VdomElement,
    loginRequired : Boolean = true
  ) : MgmtPage = new MgmtPage {
    override val routeInfo: RouteInfo[MgmtPage] = RouteInfo[MgmtPage](
      name = i18n.tr(name),
      routerPath = Path(routerPath),
      content = content,
      loginRequired = loginRequired
    )
  }
}

