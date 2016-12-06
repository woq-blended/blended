package blended.mgmt.ui.components

import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.vdom.prefix_<^._

object CompContainerInfo {

  val CompContainerInfo =
    ReactComponentB[ContainerInfo]("ContainerInfo")
      .render_P(ct =>
        <.tr(
          <.td(ct.containerId),
          <.td(ct.profiles.size, " Profiles")
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
