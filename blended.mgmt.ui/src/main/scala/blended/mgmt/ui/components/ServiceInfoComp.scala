package blended.mgmt.ui.components

import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.ServiceInfo
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object ServiceInfoComp {

  private[this] val log = Logger[ServiceInfoComp.type]
  private[this] val i18n = I18n()

  class Backend(scope: BackendScope[ServiceInfo, Unit]) {

    def render(info : ServiceInfo) = <.div(
      ^.cls := "panel panel-default",
      <.div(
        <.h3(i18n.tr("Service"), " ", info.name),
        <.h4(i18n.tr("Type: "), info.serviceType),
        <.div(
          DataTableComp.Component(DataTableContent(title = "Service Properties", content = info.props))
        )
      )
    )
  }

  val Component = ScalaComponent.builder[ServiceInfo]("ServiceInfo")
    .renderBackend[Backend]
    .build

}
