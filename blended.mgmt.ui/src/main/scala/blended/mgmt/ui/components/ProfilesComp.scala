package blended.mgmt.ui.components

import blended.mgmt.ui.backend.DataManager
import blended.mgmt.ui.components.filter.And
import blended.updater.config.ContainerInfo

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.BackendScope
import japgolly.scalajs.react.vdom.prefix_<^._

import blended.mgmt.ui.ConsoleSettings
import blended.mgmt.ui.backend.DirectProfileUpdater
import blended.updater.config.Profile
import blended.mgmt.ui.backend.Observer
import blended.mgmt.ui.components.filter.Filter
import blended.mgmt.ui.util.Logger
import blended.mgmt.ui.util.I18n

object ProfilesComp {

  private[this] val log: Logger = Logger[ProfilesComp.type]
  private[this] val i18n = I18n()

  case class State(profiles: List[Profile], filter: And[Profile] = And(), selected: Option[Profile] = None) {
    def filteredProfiles: List[Profile] = profiles.filter(c => filter.matches(c))
    def consistent = this.copy(selected = selected.filter(s => profiles.filter(c => filter.matches(c)).exists(_ == s)))
  }

  // TODO: refactor shared code with CompManagementConsole

  class Backend(scope: BackendScope[Unit, State]) extends Observer[List[Profile]] {

    override def update(newData: List[Profile]): Unit = scope.setState(State(newData).consistent).runNow()

    def addFilter(filter: Filter[Profile]) = {
      log.debug("addFilter called with filter: " + filter + ", current state: " + scope.state.runNow())
      scope.modState(s => s.copy(filter = s.filter.append(filter).normalized).consistent).runNow()
    }

    def removeFilter(filter: Filter[Profile]) = {
      scope.modState(s => s.copy(filter = And((s.filter.filters.filter(_ != filter)): _*)).consistent).runNow()
    }

    def removeAllFilter() = {
      scope.modState(s => s.copy(filter = And()).consistent).runNow()
    }

    def selectContainer(profile: Option[Profile]): Callback = {
      scope.modState(s => s.copy(selected = profile).consistent)
    }

    def render(s: State) = {
      log.debug(s"Rerendering with state $s")

      val renderedProfiles = s.filteredProfiles.map { p =>
        <.div(
          <.span(
            ^.onClick --> selectContainer(Some(p)),
            p.name,
            " ",
            p.version,
            " "
          )
        )
      }

      <.div(
        ^.`class` := "row",
        <.div(renderedProfiles: _*),
        <.div(
          ProfileDetailComp.Component(ProfileDetailComp.Props(s.selected)))
      )
    }
  }

  val Component =
    ReactComponentB[Unit]("Profiles")
      .initialState(State(profiles = List()))
      .renderBackend[Backend]
      .componentDidMount(c => DataManager.profilesData.addObserver(c.backend))
      .componentWillUnmount(c => DataManager.profilesData.removeObserver(c.backend))
      .build
}