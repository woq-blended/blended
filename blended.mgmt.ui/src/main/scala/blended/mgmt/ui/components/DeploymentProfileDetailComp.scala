package blended.mgmt.ui.components

import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

/**
 * React component to render details of a [[ContainerInfo]].
 */
object DeploymentProfileDetailComp {

  private[this] val log = Logger[DeploymentProfileDetailComp.type]
  private[this] val i18n = I18n()

  case class Props(profile: Option[SingleProfile] = None)

  class Backend(scope: BackendScope[Props, Unit]) {

    def render(props: Props) = {
      props.profile match {
        case None => <.span(i18n.tr("No Profile selected"))
        case Some(profile) =>

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
            )
          )

      }
    }
  }

  val Component = ScalaComponent.builder[Props]("DeploymentProfileDetail")
    .renderBackend[Backend]
    .build

  def apply(profile: Option[SingleProfile]) = Component(Props(profile))
}