package blended.mgmt.ui.components

import blended.updater.config.{ContainerInfo, Profile, ServiceInfo}
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ReactComponentB}
import org.scalajs.dom.ext.Ajax

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Success

import blended.updater.config.JsonProtocol._
import upickle.default._

object CompManagementConsole {

  val url = "http://localhost:9999/empty"

  case class State(containerList: List[ContainerInfo])

  class Backend($: BackendScope[_, State]) {

    val loadContainerList = Callback {

      println("Loading containers ... ")
      Ajax.get(url).onComplete {
        case Success(xhr) =>
          println(xhr.responseText)
          val newList = upickle.default.read[List[ContainerInfo]](xhr.responseText)
          $.setState(State(newList)).runNow()
        case _ => println("Could not retrieve container list from server")
      }
    }

    def render(s : State) = {
      println(s"Rerendering with $s")

      <.div(
        <.div("My cool Menu"),
        <.div(CompContainerInfo.CompContainerInfoList(s.containerList))
      )

    }
  }

  val CompManagementConsole =
    ReactComponentB[Unit]("MgmtConsole")
      .initialState(State(containerList = List.empty))
      .renderBackend[Backend]
      .componentDidMount ( _.backend.loadContainerList )
      .build
}
