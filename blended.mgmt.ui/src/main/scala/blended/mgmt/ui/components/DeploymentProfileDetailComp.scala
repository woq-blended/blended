package blended.mgmt.ui.components

import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.ReactEventI
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.util.I18n
import blended.mgmt.ui.util.Logger
import japgolly.scalajs.react.BackendScope
import blended.updater.config.OverlayState
import blended.updater.config.Profile
import blended.updater.config.OverlaySet
import japgolly.scalajs.react.CallbackTo
import blended.mgmt.ui.backend.ProfileUpdater
import blended.updater.config.ActivateProfile
import blended.updater.config.UpdateAction
import blended.updater.config.StageProfile
import blended.updater.config.Profile.SingleProfile

/**
 * React component to render details of a [[ContainerInfo]].
 */
object DeploymentProfileDetailComp {

  private[this] val log = Logger[DeploymentProfileDetailComp.type]
  private[this] val i18n = I18n()

  case class Props(profile: Option[Profile] = None)

  class Backend(scope: BackendScope[Props, Unit]) {

    def render(props: Props) = {
      props match {
        case Props(None) => <.span(i18n.tr("No Profile selected"))
        case Props(Some(profile)) =>

          val overlays = profile.overlays.map { os =>
            <.div(
              os.overlays.map(o => o.name + " " + o.version).mkString(", "),
              " (",
              os.state.state,
              ")"
            )
          }

          <.div(
            <.div(
              i18n.tr("Deployment Profile:"),
              " ",
              profile.name
            ),
            <.div(
              i18n.tr("Version:"),
              " ",
              profile.version
            ),
            <.div(
              i18n.tr("Overlays:"),
              overlays
            )
          )

      }
    }
  }

  val Component =
    ReactComponentB[Props]("DeploymentProfileDetail")
      .renderBackend[Backend]
      .build
}