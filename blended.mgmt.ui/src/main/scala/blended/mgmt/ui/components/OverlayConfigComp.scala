package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{DataManager, Observer}
import blended.mgmt.ui.components.filter.{And, Filter}
import blended.mgmt.ui.routes.{MgmtPage, NavigationInfo}
import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.OverlayConfig
import japgolly.scalajs.react.{Callback, _}
import japgolly.scalajs.react.vdom.html_<^._

object OverlayConfigComp {

  private[this] val log: Logger = Logger[OverlayConfigComp.type]
  private[this] val i18n = I18n()

  case class State(overlays: List[OverlayConfig], filter: And[OverlayConfig] = And(), selected: Option[OverlayConfig] = None) {
    def filteredOverlayConfigs: List[OverlayConfig] = overlays.filter(c => filter.matches(c))
    def consistent = this.copy(selected = selected.filter(s => overlays.filter(c => filter.matches(c)).exists(_ == s)))
  }

  class Backend(scope: BackendScope[NavigationInfo[MgmtPage], State]) extends Observer[List[OverlayConfig]] {

    override val dataChanged = { newData: List[OverlayConfig] =>
      scope.modState(_.copy(overlays = newData))
    }

    def addFilter(filter: Filter[OverlayConfig]) = {
      log.debug("addFilter called with filter: " + filter + ", current state: " + scope.state.runNow())
      scope.modState(s => s.copy(filter = s.filter.append(filter).normalized).consistent).runNow()
    }

    def removeFilter(filter: Filter[OverlayConfig]) = {
      scope.modState(s => s.copy(filter = And((s.filter.filters.filter(_ != filter)): _*)).consistent).runNow()
    }

    def removeAllFilter() = {
      scope.modState(s => s.copy(filter = And()).consistent).runNow()
    }

    def selectContainer(profile: Option[OverlayConfig]): Callback = {
      scope.modState(s => s.copy(selected = profile).consistent)
    }

    def render(p: NavigationInfo[MgmtPage], s: State) = {
      log.debug(s"Rerendering with state $s")


      val renderedOverlays = s.filteredOverlayConfigs.map { p =>
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
        <.div(renderedOverlays: _*),
        <.div(
          OverlayConfigDetailComp.Component(OverlayConfigDetailComp.Props(s.selected)))
      )
    }
  }

  val Component = ScalaComponent.builder[NavigationInfo[MgmtPage]]("OverlayConfig")
    . initialState(State(overlays = List()))
    .renderBackend[Backend]
    .componentDidMount(c => Callback {
      DataManager.overlayConfigsData.addObserver(c.backend)
    })
    .componentWillUnmount{c => Callback {
      DataManager.overlayConfigsData.removeObserver(c.backend)
    }}
    .build

  def apply(n: NavigationInfo[MgmtPage]) = Component(n)
}