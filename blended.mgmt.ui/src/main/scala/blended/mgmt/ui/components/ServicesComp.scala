package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{DataManager, Observer}
import blended.mgmt.ui.components.filter.And
import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.ServiceInfo
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object ServicesComp {

  private[this] val log = Logger[ServicesComp.type]
  private[this] val i18n = I18n()

  case class State(serviceList: List[ServiceInfo], filter: And[ServiceInfo] = And(), selected: Option[ServiceInfo] = None) {
    def filteredServiceList: List[ServiceInfo] = serviceList.filter(s => filter.matches(s))
    def consistent = this.copy(selected = selected.filter(outer => serviceList.filter( inner => filter.matches(inner)).exists(_ == outer)))
  }

  class Backend(scope: BackendScope[Unit, State]) extends Observer[List[ServiceInfo]] {

    override val dataChanged = { newData : List[ServiceInfo] =>
      scope.modState(_.copy(serviceList = newData))
    }

    def render(s: State) = {

      def services = s.serviceList.map(svc => <.div(ServiceInfoComp.Component(svc)))

      <.div(
        <.h1("Services"),
        <.div(TagMod(services:_*))
      )
    }
  }

  val Component = ScalaComponent.builder[Unit]("Services")
      .initialState(State(serviceList = List.empty))
      .renderBackend[Backend]
      .componentDidMount(c => DataManager.serviceData.addObserver(c.backend))
      .componentWillUnmount(c => DataManager.serviceData.removeObserver(c.backend))
      .build
}
