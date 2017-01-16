package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{ DataManager, Observer }
import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react.{ BackendScope, ReactComponentB }
import blended.mgmt.ui.util.Logger
import blended.mgmt.ui.util.I18n

object CompManagementConsole {

  private[this] val log: Logger = Logger[CompManagementConsole.type]
  private[this] val i18n = I18n()

  case class State(containerList: List[ContainerInfo])

  class Backend($: BackendScope[_, State]) extends Observer[List[ContainerInfo]] {

    override def update(newData: List[ContainerInfo]): Unit = $.setState(State(newData)).runNow()

    def render(s: State) = {
      log.debug(s"Rerendering with $s")

      <.div(CompContainerInfo.CompContainerInfoList(s.containerList))
    }
  }

  val CompManagementConsole =
    ReactComponentB[Unit]("MgmtConsole")
      .initialState(State(containerList = List.empty))
      .renderBackend[Backend]
      .componentDidMount(c => DataManager.containerData.addObserver(c.backend))
      .componentWillUnmount(c => DataManager.containerData.removeObserver(c.backend))
      .build
}
