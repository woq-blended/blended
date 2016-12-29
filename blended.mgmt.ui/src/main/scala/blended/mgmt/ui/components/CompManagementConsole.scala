package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{DataManager, Observer}
import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react.{BackendScope, ReactComponentB}

object CompManagementConsole {

  case class State(containerList: List[ContainerInfo])

  class Backend($: BackendScope[_, State]) extends Observer[List[ContainerInfo]] {

    override def update(newData: List[ContainerInfo]): Unit = $.setState(State(newData)).runNow()

    def render(s : State) = {
      println(s"Rerendering with $s")

      <.div(
        <.div("My very super cool Menu"),
        <.div(CompContainerInfo.CompContainerInfoList(s.containerList))
      )
    }
  }

  val CompManagementConsole =
    ReactComponentB[Unit]("MgmtConsole")
      .initialState(State(containerList = List.empty))
      .renderBackend[Backend]
      .componentDidMount (c => DataManager.containerData.addObserver(c.backend))
      .componentWillUnmount(c => DataManager.containerData.removeObserver(c.backend))
      .build
}
