package blended.mgmt.ui.pages

import blended.mgmt.ui.components.LoginComponent
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react._
import vdom.html_<^._

object LoginPage extends TopLevelPage {

  override val name = i18n.tr("Login")
  override val routerPath = Path("#login")
  override val content : VdomElement = LoginComponent()()

  override def requiresLogin : Boolean = false
}
