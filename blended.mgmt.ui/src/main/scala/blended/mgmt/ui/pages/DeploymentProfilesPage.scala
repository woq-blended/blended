package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.components.DeploymentProfilesComp

object DeploymentProfilesPage extends TopLevelPage {

  override val name: String = i18n.tr("Deployment Profiles")

  override val routerPath: Path = Path("#deploymentProfiles")

  override val content: ReactElement = <.p(
    <.div(DeploymentProfilesComp.Component())
  )
}