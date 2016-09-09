package blended.mgmt.ui

import blended.updater.config.{ContainerInfo, Profile, ServiceInfo}

import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react.{ReactComponentB, ReactDOM}
import org.scalajs.dom
import scala.collection.immutable

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

@JSExport
class MgmtConsole extends js.JSApp {

  val Hello =
    ReactComponentB[ContainerInfo]("Hello")
      .render_P(ct => <.div("Container Info ", ct.containerId))
      .build

  override def main(): Unit =
    ReactDOM.render(Hello(dummy), dom.document.getElementById("content"))

  lazy val dummy : ContainerInfo = ContainerInfo(
    "myCoolContainer",
    Map( "foo" -> "bar"),
    immutable.Seq.empty[ServiceInfo],
    immutable.Seq.empty[Profile]
  )
}
