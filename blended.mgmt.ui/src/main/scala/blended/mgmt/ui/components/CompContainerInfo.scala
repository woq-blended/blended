package blended.mgmt.ui.components

import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.{ Callback, ReactComponentB, ReactEventI }
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.util.I18n
import blended.mgmt.ui.util.Logger
import japgolly.scalajs.react.BackendScope

object CompContainerInfo {

  private[this] val log = Logger[CompContainerInfo.type]
  private[this] val i18n = I18n()

  case class Props(containerInfo: ContainerInfo, select: () => Unit)

  class CompContainerInfoBackend(scope: BackendScope[Props, Unit]) {
    def selectContainer(e: ReactEventI) = {
      scope.props.map { p => p.select() }
    }

    def render(p: Props) = {
      val ct = p.containerInfo
      <.tr(
        <.td(
          <.span(
            ^.onClick ==> selectContainer,
            ct.containerId)
        ),
        <.td(
          i18n.trn("{0} Profile", "{0} Profiles", ct.profiles.size, ct.profiles.size)
        ))
    }
  }

  val Component =
    ReactComponentB[Props]("ContainerInfo")
      .renderBackend[CompContainerInfoBackend]
      .build

}
