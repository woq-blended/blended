package blended.mgmt.ui.components

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import blended.mgmt.ui.util.I18n
import blended.mgmt.ui.components.filter.Filter
import blended.updater.config.ContainerInfo
import blended.mgmt.ui.util.Logger
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.BackendScope
import blended.mgmt.ui.components.filter.ContainerInfoFilter
import blended.mgmt.ui.components.filter.And

object ContainerInfoFilterComp {

  private[this] val i18n = I18n()
  private[this] val log: Logger = Logger[ContainerInfoFilterComp.type]

  case class Props(
    filter: Filter[ContainerInfo],
    containerInfos: List[ContainerInfo],
    addFilter: Filter[ContainerInfo] => Unit)

  case class State(searchText: String = "", containerId: String = "", propertyName: String = "", propertyValue: String = "") {
    def toFilter(): Filter[ContainerInfo] = {
      var filter = Seq[Filter[ContainerInfo]]()
      if (!searchText.isEmpty) filter ++= Seq(ContainerInfoFilter.FreeText(searchText))
      if (!containerId.isEmpty) filter ++= Seq(ContainerInfoFilter.ContainerId(containerId))
      if (!propertyName.isEmpty && !propertyValue.isEmpty) filter ++= Seq(ContainerInfoFilter.Property(propertyName, propertyValue))
      val f = And(filter: _*)
      log.trace("state: " + this + ", toFilter: " + f)
      f
    }
  }

  class Backend(scope: BackendScope[Props, State]) {

    def onSubmit(e: ReactEvent): Callback = {
      log.trace("onSubmit: " + e)
      e.preventDefaultCB >>
        scope.state.flatMap { s =>
          val newFilter = s.toFilter()
          scope.setState(State()).runNow()
          scope.props.map(_.addFilter(newFilter))
        }
    }

    def onSearchTextChange(e: ReactEventFromTextArea): Callback = {
      e.extract(_.target.value) { v =>
        log.trace("search text: " + v)
        scope.modState(_.copy(searchText = v))
      }
    }

    def onContainerIdChange(e: ReactEventFromTextArea): Callback = {
      e.extract(_.target.value) { v =>
        log.trace("container ID: " + v)
        scope.modState(_.copy(containerId = v))
      }
    }
    def onPropertyNameChange(e: ReactEventFromInput): Callback = {
      e.extract(_.target.value) { v =>
        log.trace("property name: " + v)
        scope.modState(_.copy(propertyName = v))
      }
    }

    def onPropertyValueChange(e: ReactEventFromTextArea): Callback = {
      e.extract(_.target.value) { v =>
        log.trace("property value: " + v)
        scope.modState(_.copy(propertyValue = v))
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
      <.div(
        ^.border := "1px solid",
        ^.borderRadius := "5px",
        ^.margin := "5px",
        ^.padding := "30px",
        <.form(
          ^.onSubmit ==> onSubmit,
          <.div(
            ^.cls := "form-group row",
            <.label(
              ^.`for` := "searchText",
              ^.cls := "col-sm-2 col-form-label",
              i18n.tr("Search")
            ),
            <.div(
              ^.cls := "col-sm-10",
              <.input(
                ^.id := "searchText",
                ^.`type` := "text",
                ^.cls := "form-control",
                ^.value := state.searchText,
                ^.onChange ==> onSearchTextChange
              )
            )
          ),
          <.div(
            ^.cls := "form-group row",
            <.label(
              ^.`for` := "containerId",
              ^.cls := "col-sm-2 col-form-label",
              i18n.tr("Container ID")
            ),
            <.div(
              ^.cls := "col-sm-10",
              <.input(
                ^.id := "containerId",
                ^.`type` := "text",
                ^.cls := "form-control",
                ^.value := state.containerId,
                ^.onChange ==> onContainerIdChange
              )
            )
          ),
          <.div(
            ^.cls := "form-group row",
            <.div(
              ^.cls := "col-sm-2",
              <.label(
                ^.`for` := "propValue",
                ^.cls := " col-form-label",
                i18n.tr("Property")
              ),
              <.select(
                (Seq(^.onChange ==> onPropertyNameChange) ++
                  propKeys.map(p => <.option(^.value := p,
                    // TODO: help needed here, don't know how to apply the "selected" option
                    //                  (state.propertyName == p) ?= ^.selected := "selected",
                    p))
                  ): _*
              )
            ),
            <.div(
              ^.cls := "col-sm-10",
              <.input(
                ^.id := "propValue",
                ^.`type` := "text",
                ^.cls := "form-control",
                // ^.list := "propvalues",
                ^.value := state.propertyValue,
                ^.onChange ==> onPropertyValueChange
              ) // ,
            )

          // <.datalist(^.id := "propvalues")
          ),
          <.div(
            ^.cls := "form-group row",
            <.input(
              ^.`type` := "submit",
              ^.cls := "btn btn-primary pull-right ",
              ^.value := "Filter"
            )
          )
        )
      )
    }
  }

  val Component = ScalaComponent.builder[Props]("ContainerEditFilter")
    .initialState(State())
    .backend(new Backend(_))
    .renderBackend
    .build

}