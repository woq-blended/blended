package blended.mgmt.ui.components

import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.{ContainerInfo, Profile}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

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
              i18n.tr("Overlays:")
              // TODO: Review
              // overlays
            )
          )

      }
    }
  }

  val Component = ScalaComponent.builder[Props]("DeploymentProfileDetail")
    .renderBackend[Backend]
    .build
}