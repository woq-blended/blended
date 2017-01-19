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
      <.div(
        i18n.tr("Container ID: {0}", ci.map(_.containerId).getOrElse(i18n.tr("<NONE>")))
      )
    }
  }

  val Component =
    ReactComponentB[Option[ContainerInfo]]("ContainerDetail")
      .renderBackend[Backend]
      .build
}