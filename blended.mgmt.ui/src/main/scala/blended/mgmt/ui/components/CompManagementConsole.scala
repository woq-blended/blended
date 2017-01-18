package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{ DataManager, Observer }
import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react.{ BackendScope, ReactComponentB, Callback }
import blended.mgmt.ui.util.Logger
import blended.mgmt.ui.util.I18n
import blended.mgmt.ui.components.filter.Filter
import blended.mgmt.ui.components.filter.And

object CompManagementConsole {

  private[this] val log: Logger = Logger[CompManagementConsole.type]
  private[this] val i18n = I18n()

  case class State(containerList: List[ContainerInfo], filter: And[ContainerInfo] = And()) {
    def filteredContainerList: List[ContainerInfo] = containerList.filter(c => filter.matches(c))
  }

  class Backend(scope: BackendScope[Unit, State]) extends Observer[List[ContainerInfo]] {

    override def update(newData: List[ContainerInfo]): Unit = scope.setState(State(newData)).runNow()

    def addFilter(filter: Filter[ContainerInfo]) = {
      log.debug("addFilter called with filter: " + filter + ", current state: " + scope.state.runNow())
      scope.modState(s => s.copy(filter = s.filter.append(filter).normalized)).runNow()
    }

    def removeFilter(filter: Filter[ContainerInfo]) = {
      scope.modState(s => s.copy(filter = And((s.filter.filters.filter(_ != filter)): _*))).runNow()
    }

    def removeAllFilter() = {
      scope.modState(s => s.copy(filter = And())).runNow()
    }

    def render(s: State) = {
      log.debug(s"Rerendering with $s")
      <.div(
        <.div(
          CompEditFilter.CompEditFilter(CompEditFilter.Props(s.filter, s.containerList, addFilter))),
        <.div(
          CompViewFilter.CompViewFilter(CompViewFilter.Props(s.filter, removeFilter, removeAllFilter))),
        <.div(
          CompContainerInfo.CompContainerInfoList(s.filteredContainerList))
      )
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
