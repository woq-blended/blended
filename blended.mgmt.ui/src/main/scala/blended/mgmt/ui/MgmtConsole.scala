package blended.mgmt.ui

import blended.mgmt.ui.components.CompManagementConsole._
import japgolly.scalajs.react.ReactDOM
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

@JSExport
class MgmtConsole extends js.JSApp {

  override def main(): Unit =
    ReactDOM.render(CompManagementConsole(), dom.document.getElementById("content"))

}
