package blended.mgmt.ui.components

import blended.mgmt.ui.backend.DataManager
import blended.mgmt.ui.components.filter.And

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.BackendScope
import japgolly.scalajs.react.vdom.prefix_<^._

import blended.mgmt.ui.ConsoleSettings
import blended.mgmt.ui.backend.Observer
import blended.mgmt.ui.components.filter.Filter
import blended.mgmt.ui.util.Logger
import blended.mgmt.ui.util.I18n
import blended.updater.config.RuntimeConfig

object RuntimeConfigsComp {

  private[this] val log: Logger = Logger[RuntimeConfigsComp.type]
  private[this] val i18n = I18n()

  case class State(profiles: List[RuntimeConfig], filter: And[RuntimeConfig] = And(), selected: Option[RuntimeConfig] = None) {
    def filteredRuntimeConfigs: List[RuntimeConfig] = profiles.filter(c => filter.matches(c))
    def consistent = this.copy(selected = selected.filter(s => profiles.filter(c => filter.matches(c)).exists(_ == s)))
  }

  // TODO: refactor shared code with CompManagementConsole

  class Backend(scope: BackendScope[Unit, State]) extends Observer[List[RuntimeConfig]] {

    override def update(newData: List[RuntimeConfig]): Unit = scope.setState(State(newData).consistent).runNow()

    def addFilter(filter: Filter[RuntimeConfig]) = {
      log.debug("addFilter called with filter: " + filter + ", current state: " + scope.state.runNow())
      scope.modState(s => s.copy(filter = s.filter.append(filter).normalized).consistent).runNow()
    }

    def removeFilter(filter: Filter[RuntimeConfig]) = {
      scope.modState(s => s.copy(filter = And((s.filter.filters.filter(_ != filter)): _*)).consistent).runNow()
    }

    def removeAllFilter() = {
      scope.modState(s => s.copy(filter = And()).consistent).runNow()
    }

    def selectContainer(profile: Option[RuntimeConfig]): Callback = {
      scope.modState(s => s.copy(selected = profile).consistent)
    }

    def render(s: State) = {
      log.debug(s"Rerendering with state $s")

      val renderedConfigs = s.filteredRuntimeConfigs.map { p =>
        <.div(
          <.span(
            ^.onClick --> selectContainer(Some(p)),
            p.name,
            "-",
            p.version
          )
        )
      }

      <.div(
        ^.`class` := "row",
        <.div(renderedConfigs: _*),
        <.div(
          RuntimeConfigDetailComp.Component(RuntimeConfigDetailComp.Props(s.selected)))
      )
    }
  }

  val Component =
    ReactComponentB[Unit]("Runtime Configs").
      initialState(State(profiles = List()))
      .renderBackend[Backend]
      .componentDidMount(c => DataManager.runtimeConfigsData.addObserver(c.backend))
      .componentWillUnmount(c => DataManager.runtimeConfigsData.removeObserver(c.backend))
      .build
}