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

object CompViewFilter {

  private[this] val i18n = I18n()
  private[this] val log: Logger = Logger[CompViewFilter.type]

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

      val selectedFilters = filters.map { filter =>
        <.span(
          <.span(filter.toString()),
          <.span(
            i18n.trc("Remove filter", "X"),
            ^.onClick ==> removeFilter(filter)
          )
        )
      }

      val clearFilters = filters.headOption.map(_ => <.span(
        i18n.tr("Clear Filter"),
        ^.onClick ==> removeAllFilter))

      <.span((selectedFilters ++ clearFilters): _*)
    }
  }

  val CompViewFilter = ReactComponentB[Props]("ContainerViewFilter")
    .backend(new Backend(_))
    .renderBackend
    .build

}