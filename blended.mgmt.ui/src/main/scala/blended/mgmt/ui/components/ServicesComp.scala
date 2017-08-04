package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{DataManager, Observer}
import blended.mgmt.ui.components.filter.And
import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.ServiceInfo
import japgolly.scalajs.react.{BackendScope, ReactComponentB}
import japgolly.scalajs.react.vdom.prefix_<^._

object ServicesComp {

  private[this] val log = Logger[ServicesComp.type]
  private[this] val i18n = I18n()

  case class State(serviceList: List[ServiceInfo], filter: And[ServiceInfo] = And(), selected: Option[ServiceInfo] = None) {
    def filteredServiceList: List[ServiceInfo] = serviceList.filter(s => filter.matches(s))
    def consistent = this.copy(selected = selected.filter(outer => serviceList.filter( inner => filter.matches(inner)).exists(_ == outer)))
  }

  class Backend(scope: BackendScope[Unit, State]) extends Observer[List[ServiceInfo]] {
    override def update(newData: List[ServiceInfo]): Unit = scope.setState(State(newData).consistent).runNow()

    def render(s: State) = {
      log.debug(s"Rerendering with $s")
      <.h1("Services")
    }
  }

  val Component =
    ReactComponentB[Unit]("Services")
      .initialState(State(serviceList = List.empty))
      .renderBackend[Backend]
      .componentDidMount(c => DataManager.serviceData.addObserver(c.backend))
      .componentWillUnmount(c => DataManager.serviceData.removeObserver(c.backend))
      .build
}
