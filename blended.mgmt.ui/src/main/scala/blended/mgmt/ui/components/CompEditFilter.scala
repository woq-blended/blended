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

object CompEditFilter {

  private[this] val i18n = I18n()
  private[this] val log: Logger = Logger[CompEditFilter.type]

  case class Props(
    filter: Filter[ContainerInfo],
    containerInfos: List[ContainerInfo],
    addFilter: Filter[ContainerInfo] => Unit)

  case class State(searchText: String = "", containerId: String = "") {
    def toFilter(): Filter[ContainerInfo] = {
      var filter = Seq[Filter[ContainerInfo]]()
      if (!searchText.isEmpty) filter ++= Seq(ContainerInfoFilter.FreeText(searchText))
      if (!containerId.isEmpty) filter ++= Seq(ContainerInfoFilter.ContainerId(containerId))
      val f = And(filter: _*)
      log.trace("state: " + this + ", toFilter: " + f)
      f
    }
  }

  class Backend(scope: BackendScope[Props, State]) {

    def onSubmit(e: ReactEventI): Callback = {
      log.trace("onSubmit: " + e)
      e.preventDefaultCB >>
        scope.state.flatMap { s =>
          val newFilter = s.toFilter()
          scope.setState(State()).runNow()
          scope.props.map(_.addFilter(newFilter))
        }
    }

    def onSearchTextChange(e: ReactEventI): Callback = {
      e.extract(_.target.value) { v =>
        log.trace("search text: " + v)
        scope.modState(_.copy(searchText = v))
      }
    }

    def onContainerIdChange(e: ReactEventI): Callback = {
      e.extract(_.target.value) { v =>
        log.trace("container ID: " + v)
        scope.modState(_.copy(containerId = v))
      }
    }

    def render(props: Props, state: State) = {
      val properties = props.containerInfos.flatMap(ci => ci.properties.toSeq).groupBy(_._1).mapValues(_.map(_._2).distinct)
      val propKeys = properties.keySet.toList.sorted
      log.trace("Found properties: " + properties)

      //      case And(filters) =>
      //        // a list of potential filters with either the choosable options or the selected value
      //        filters.collect {
      //          case ContainerInfoFilter.
      //        }}
      <.form(
        ^.onSubmit ==> onSubmit,
        <.div(
          <.span(i18n.tr("Search")),
          <.input(
            ^.`type` := "text",
            ^.value := state.searchText,
            ^.onChange ==> onSearchTextChange
          )
        ),
        <.div(
          <.span(i18n.tr("Container ID")),
          <.input(
            ^.`type` := "text",
            ^.value := state.containerId,
            ^.onChange ==> onContainerIdChange
          )
        ),
        <.div(
          <.span(i18n.tr("Property")),
          <.select(
            propKeys.map(p => <.option(^.value := p, p)): _*
          ),
          <.input(
            ^.`type` := "text",
            ^.list := "propvalues"
          ),
          <.datalist(^.id := "propvalues")
        ),
        <.input(
          ^.`type` := "submit",
          ^.value := "Filter"
        )
      )
    }
  }

  val CompEditFilter = ReactComponentB[Props]("ContainerEditFilter")
    .initialState(State())
    .backend(new Backend(_))
    .renderBackend
    .build

}