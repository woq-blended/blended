package blended.mgmt.ui

import blended.mgmt.ui.styles.AppStyles
import japgolly.scalajs.react.extra.router._
import org.scalajs.dom

import scala.scalajs.js

object MgmtConsole extends js.JSApp {

  val baseUrl =
      BaseUrl.fromWindowOrigin / "management/"

  def main(): Unit = {

    AppStyles.load()

    val router = Router(baseUrl, MgmtRouter.routerConfig.logToConsole)

    router().renderIntoDOM(dom.document.getElementById("content"))
  }
}
