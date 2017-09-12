package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{DataManager, Observer}
import blended.mgmt.ui.components.filter.{And, Filter}
import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.RuntimeConfig
import japgolly.scalajs.react.{Callback, _}
import japgolly.scalajs.react.vdom.html_<^._

object ProfilesComp {

  private[this] val log: Logger = Logger[ProfilesComp.type]
  private[this] val i18n = I18n()

  case class State(
    profiles: List[RuntimeConfig],
    filter: And[RuntimeConfig] = And(),
    selected: Option[RuntimeConfig] = None
  ) {
    def filteredRuntimeConfigs: List[RuntimeConfig] = profiles.filter(c => filter.matches(c))
    def consistent = this.copy(selected = selected.filter(s => profiles.filter(c => filter.matches(c)).exists(_ == s)))
  }

  // TODO: refactor shared code with CompManagementConsole

  class Backend(scope: BackendScope[Unit, State]) extends Observer[List[RuntimeConfig]] {

    override val dataChanged = { newData: List[RuntimeConfig] =>
      scope.modState(_.copy(profiles = newData))
    }

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

    def selectProfile(profile: Option[RuntimeConfig]): Callback = {
      scope.modState(s => s.copy(selected = profile).consistent)
    }

    def render(s: State) = {
      log.debug(s"Rerendering with state $s")

      val renderedConfigs = s.filteredRuntimeConfigs.map { p =>
        <.div(
          <.span(
            ^.onClick --> selectProfile(Some(p)),
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
          ProfileDetailComp.Component(ProfileDetailComp.Props(s.selected)))
      )
    }
  }

  val Component = ScalaComponent.builder[Unit]("Profiles")
    .initialState(State(profiles = List()))
    .renderBackend[Backend]
    .componentDidMount(c => Callback { DataManager.runtimeConfigsData.addObserver(c.backend)})
    .componentWillUnmount(c => Callback { DataManager.runtimeConfigsData.removeObserver(c.backend)})
    .build
}