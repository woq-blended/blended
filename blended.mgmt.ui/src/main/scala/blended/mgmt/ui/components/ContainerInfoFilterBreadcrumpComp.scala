package blended.mgmt.ui.components

import blended.mgmt.ui.components.filter.{And, Filter}
import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.{BackendScope, Callback, _}
import japgolly.scalajs.react.vdom.html_<^._

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

    def removeFilter(filter: Filter[ContainerInfo])(e: ReactEvent): Callback = {
      scope.props.map(_.removeFilter(filter))
    }

    def removeAllFilter(e: ReactEvent): Callback = {
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

      filters match {
        case Nil => <.div()
        case f =>
          <.div(
            ^.cls := "panel panel-default",
            <.div(
              ^.cls := "panel-heading",
              i18n.tr("Applied Filters")
            ),
            <.div(
              ^.cls := "panel-body",
              <.div(TagMod(selectedFilters:_*)),
              TagMod(clearFilters:_*)
            )
          )
      }
    }
  }

  val Component = ScalaComponent.builder[Props]("ContainerViewFilter")
    .backend(new Backend(_))
    .renderBackend
    .build

}