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
          <.td(i18n.trn("{0} Profile", "{0} Profiles", ct.profiles.size, ct.profiles.size))
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
