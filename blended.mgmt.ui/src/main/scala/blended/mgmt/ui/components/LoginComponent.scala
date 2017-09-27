package blended.mgmt.ui.components

import blended.mgmt.ui.routes.NavigationInfo
import blended.mgmt.ui.util.{I18n, Logger}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

object LoginComponent {

  def apply[T](target: T)(n: NavigationInfo[T]) = new LoginComponent[T](target).component(n)
}

class LoginComponent[T](target : T) {

  val log : Logger = Logger[LoginComponent.type]
  val i18n = I18n()

  class Backend(scope: BackendScope[NavigationInfo[T], Unit]) {

    def render : VdomElement = <.div(
      "login"
    )
  }

  val component = ScalaComponent.builder[NavigationInfo[T]]("LoginComponent")
    .renderBackend[Backend]
    .build

}
