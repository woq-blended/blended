package blended.mgmt.ui.components

import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.ReactEventI
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.util.I18n
import blended.mgmt.ui.components.filter.Filter
import blended.updater.config.ContainerInfo
import blended.mgmt.ui.util.Logger
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.BackendScope
import blended.mgmt.ui.components.filter.ContainerInfoFilter
import blended.mgmt.ui.components.filter.And

/**
 * React component to render a breadcrumb bar showing the currently selected filters plus option to remove/reset them.
 */
object ContainerInfoFilterBreadcrumpComp {

  private[this] val i18n = I18n()
  private[this] val log: Logger = Logger[ContainerInfoFilterBreadcrumpComp.type]

  case class Props(
    filter: And[ContainerInfo],
    removeFilter: Filter[ContainerInfo] => Unit,
    removeAllFilter: () => Unit)

  class Backend(scope: BackendScope[Props, Unit]) {

    def removeFilter(filter: Filter[ContainerInfo])(e: ReactEventI): Callback = {
      scope.props.map(_.removeFilter(filter))
    }

    def removeAllFilter(e: ReactEventI): Callback = {
      scope.props.map(_.removeAllFilter())
    }

    def render(props: Props) = {

      val filters = props.filter.filters

      def button(f : Filter[ContainerInfo]) = <.div(
        ^.cls := "glyphicon glyphicon-remove glyphicon-remove-sign glyphicon-white",
        ^.onClick ==> removeFilter(f)
      )

      val selectedFilters = filters.map { filter =>
        <.div(
          ^.cls := "label label-primary",
          ^.display := "inline-block",
          ^.padding := "10px",
          ^.margin := "5px",
          filter.toString(),
          button(filter)
        )
      }

      val clearFilters = filters.headOption.map(_ => <.button(
        ^.cls := "btn btn-xs btn-danger",
        i18n.tr("Clear Filter"),
        ^.onClick ==> removeAllFilter)
      ).toList

      <.div(
        ^.cls := "panel panel-default",
        <.div(
          ^.cls := "panel-heading",
          i18n.tr("Applied Filters")
        ),
        <.div(
          ^.cls := "panel-body",
          selectedFilters,
          clearFilters
        )
      )
    }
  }

  val Component = ReactComponentB[Props]("ContainerViewFilter")
    .backend(new Backend(_))
    .renderBackend
    .build

}