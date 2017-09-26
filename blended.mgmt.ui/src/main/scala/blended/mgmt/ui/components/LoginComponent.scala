package blended.mgmt.ui.components

import blended.mgmt.ui.util.{I18n, Logger}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

object LoginComponent {

  val log : Logger = Logger[LoginComponent.type]
  val i18n = I18n()

  class Backend(scope: BackendScope[Unit, Unit]) {

    def render : VdomElement = <.div("login")
  }

  val component = ScalaComponent.builder[Unit]("LoginComponent")
    .renderBackend[Backend]
    .build

  def apply() = component
}
