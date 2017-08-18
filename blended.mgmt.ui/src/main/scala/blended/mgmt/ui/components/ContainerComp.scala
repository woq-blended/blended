package blended.mgmt.ui.components

import blended.mgmt.ui.ConsoleSettings
import blended.mgmt.ui.backend.{DataManager, DirectProfileUpdater, Observer}
import blended.mgmt.ui.components.filter.{And, Filter, FilterBackend, FilterState}
import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, ScalaComponent}

/**
 * React component showing a filterable page about containers and their details.
 *
 * @see [[ContainerInfo]]
 */
object ContainerComp {

  private[this] val log: Logger = Logger[ContainerComp.type]
  private[this] val i18n = I18n()

  class Backend(override val scope: BackendScope[Unit, FilterState[ContainerInfo]])
    extends FilterBackend[ContainerInfo]
    with Observer[List[ContainerInfo]] {

    override def dataChanged(newData: List[ContainerInfo]): Unit =
      scope.setState(FilterState[ContainerInfo](items = newData).consistent).runNow()

    def render(s: FilterState[ContainerInfo]) = {
      log.debug(s"Rerendering with [${s.items.size}] containers, selected = [${s.selected.map(_.containerId)}]")

      <.div(
        ^.`class` := "row",
        <.div(
          ContainerInfoFilterComp.Component(ContainerInfoFilterComp.Props(s.filter, s.items, addFilter))),
        <.div(
          ContainerInfoFilterBreadcrumpComp.Component(ContainerInfoFilterBreadcrumpComp.Props(s.filter, removeFilter, removeAllFilter))),
        <.div(
          ContainerInfoListComp.Component(ContainerInfoListComp.Props(s.items, selectItem))),
        <.div(
          ContainerDetailComp.Component(ContainerDetailComp.Props(s.selected, Some(new DirectProfileUpdater(ConsoleSettings.containerDataUrl)))))
      )
    }
  }

  val Component =
    ScalaComponent.builder[Unit]("Container")
      .initialState(FilterState[ContainerInfo]())
      .renderBackend[Backend]
      .componentDidMount(c => DataManager.containerData.addObserver(c.backend))
      .componentWillUnmount(c => DataManager.containerData.removeObserver(c.backend))
      .build
}
