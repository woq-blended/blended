package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{DataManager, Observer, ProfileUpdateAction}
import blended.mgmt.ui.components.filter.{FilterBackend, FilterState}
import blended.mgmt.ui.routes.{MgmtPage, NavigationInfo}
import blended.mgmt.ui.util.{I18n, LayoutHelper, Logger}
import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}

/**
 * React component showing a filterable page about containers and their details.
 *
 * @see [[ContainerInfo]]
 */
object ContainerComp {

  private[this] val log: Logger = Logger[ContainerComp.type]
  private[this] val i18n = I18n()

  class Backend(override val scope: BackendScope[NavigationInfo[MgmtPage], FilterState[ContainerInfo]])
    extends FilterBackend[NavigationInfo[MgmtPage], ContainerInfo]
    with Observer[List[ContainerInfo]] {

    override val dataChanged = { newData : List[ContainerInfo] =>
      scope.modState(_.copy(items = newData))
    }

    def render(p: NavigationInfo[MgmtPage], s: FilterState[ContainerInfo]) = {
      log.debug(s"Rerendering with [${s.items.size}] containers, selected = [${s.selected.map(_.containerId)}]")

      val filter : VdomElement = <.div(
        ^.`class` := "row",
        <.div(
          ContainerInfoFilterComp.Component(ContainerInfoFilterComp.Props(s.filter, s.items, addFilter))
        ),
        <.div(
          ContainerInfoFilterBreadcrumpComp.Component(ContainerInfoFilterBreadcrumpComp.Props(s.filter, removeFilter, removeAllFilter))
        )
      )

      val detail = <.div(
        ContainerDetailComp.Component(
          ContainerDetailComp.Props(s.selected, Some(ProfileUpdateAction.DefaultAjax))
        )
      )

      val list = <.div(
        ContainerInfoListComp.Component(ContainerInfoListComp.Props(s.items, selectItem))
      )

      LayoutHelper.splitLayout(
        main = detail,
        firstComponent = Some(filter),
        secondComponent = Some(list),
        verticalFirst = false
      )
    }
  }

  val Component =
    ScalaComponent.builder[NavigationInfo[MgmtPage]]("Container")
      .initialState(FilterState[ContainerInfo]())
      .renderBackend[Backend]
      .componentDidMount(c => Callback { DataManager.containerData.addObserver(c.backend)})
      .componentWillUnmount(c => Callback { DataManager.containerData.removeObserver(c.backend)})
      .build

  def apply(n : NavigationInfo[MgmtPage]) = Component(n)
}
