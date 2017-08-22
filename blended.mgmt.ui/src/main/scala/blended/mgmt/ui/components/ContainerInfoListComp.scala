package blended.mgmt.ui.components

import blended.mgmt.ui.styles.PanelDefault
import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.{ContainerInfo, OverlayState}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react._

import scalacss.ScalaCssReact._
import scalacss.internal.mutable.GlobalRegistry

/**
 * React Component to render a list of [[ContainerInfo]]s.
 */
object ContainerInfoListComp {

  private[this] val log = Logger[ContainerInfoListComp.type]
  private[this] val i18n = I18n()

  case class Props(containerInfos: List[ContainerInfo], selectContainer: Option[ContainerInfo] => Unit)

  class Backend(scope: BackendScope[Props, Unit]) {

    def selectContainerInfo(containerInfo: Option[ContainerInfo]): Callback = {
      scope.props.map(p => p.selectContainer(containerInfo))
    }

    def render(p: Props) = {

      val panelStyle = GlobalRegistry[PanelDefault.type].get

      val rows = p.containerInfos.map { ci =>
        val singleProfiles = ci.profiles.flatMap(_.toSingle)
        val activeProfile = singleProfiles.find(p => p.state == OverlayState.Active)

        <.div(
          ^.onClick --> selectContainerInfo(Some(ci)),
          ci.containerId,
          <.span(
            " (",
            activeProfile.get.name,
            "-",
            activeProfile.get.version,
            (i18n.tr(" with {0}", activeProfile.get.overlaySet.overlays.mkString(", "))).unless(activeProfile.get.overlaySet.overlays.isEmpty),
            ")"
          ).when(activeProfile.isDefined)
        )
      }

      <.div(
        panelStyle.container,
        <.div(
          ^.cls := "panel-heading",
          <.h3(i18n.tr("Container"))
        ),
        <.div(
          ^.cls := "panel-body",
          TagMod(rows:_*)
        )
      )
    }
  }

  val Component =
    ScalaComponent.builder[Props]("ContainerInfoList")
      .renderBackend[Backend]
      .build

}