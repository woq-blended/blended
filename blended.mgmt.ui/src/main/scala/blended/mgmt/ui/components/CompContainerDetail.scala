package blended.mgmt.ui.components

import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.util.I18n
import blended.mgmt.ui.util.Logger
import japgolly.scalajs.react.BackendScope

object CompContainerDetail {

  private[this] val log = Logger[CompContainerDetail.type]
  private[this] val i18n = I18n()

  class Backend(scope: BackendScope[Option[ContainerInfo], Unit]) {
    def render(ci: Option[ContainerInfo]) = {
      ci match {
        case None => <.span(i18n.tr("No Container selected"))
        case Some(containerInfo) =>

          val props = containerInfo.properties.map(p => <.div(<.span(p._1, ": "), <.span(p._2))).toSeq
          val profiles = containerInfo.profiles.map(p => <.span(p.name))

          <.div(
            <.div(
              i18n.tr("Container ID:"),
              containerInfo.containerId
            ),
            <.div(
              i18n.tr("Properties:"),
              <.div(props: _*)
            ),
            <.div(
              i18n.tr("Profiles:"),
              <.span(profiles: _*)
            )
          )

      }
    }
  }

  val Component =
    ReactComponentB[Option[ContainerInfo]]("ContainerDetail")
      .renderBackend[Backend]
      .build
}