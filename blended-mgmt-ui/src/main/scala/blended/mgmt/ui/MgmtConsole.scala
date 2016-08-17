package blended.mgmt.ui

import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react.{ReactComponentB, ReactDOM}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

@JSExport
class MgmtConsole extends js.JSApp {

  val Hello =
    ReactComponentB[String]("Hello")
      .render_P(name => <.div("Hello there ", name))
      .build

  override def main(): Unit =
    ReactDOM.render(Hello("Andreas"), dom.document.getElementById("content"))

}
