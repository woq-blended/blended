package blended.mgmt.ui.components

import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.{ Callback, ReactComponentB, ReactEventI }
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.util.I18n
import blended.mgmt.ui.util.Logger
import japgolly.scalajs.react.BackendScope

object CompContainerInfoList {

  private[this] val log = Logger[CompContainerInfoList.type]
  private[this] val i18n = I18n()

  case class Props(containerInfos: List[ContainerInfo], selectContainer: Option[ContainerInfo] => Unit)

  class Backend(scope: BackendScope[Props, Unit]) {
    def render(p: Props) = {
      val rows = p.containerInfos.map(ci =>
        CompContainerInfo.Component(CompContainerInfo.Props(
          ci,
          { () => p.selectContainer(Some(ci)) })))
      <.table(
        <.tbody(rows)
      )
    }
  }

  val Component =
    ReactComponentB[Props]("ContainerList")
      .renderBackend[Backend]
      .build

}