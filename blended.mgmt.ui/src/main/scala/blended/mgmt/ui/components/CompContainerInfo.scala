package blended.mgmt.ui.components

import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.util.I18n

object CompContainerInfo {
  
  private[this] val i18n = I18n()

  val CompContainerInfo =
    ReactComponentB[ContainerInfo]("ContainerInfo")
      .render_P(ct =>
        <.tr(
          <.td(ct.containerId),
          <.td(ct.profiles.size, i18n.tr(" Profiles"))
        )
      )
      .build

  val CompContainerInfoList =
    ReactComponentB[List[ContainerInfo]]("ContainerList")
      .render_P { l =>
        val rows = l.map(CompContainerInfo(_))
        <.table(
          <.tbody(rows)
        )
      }
      .build

}
