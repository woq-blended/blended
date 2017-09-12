package blended.mgmt.ui.pages

import blended.mgmt.ui.components.RolloutComponent
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.html_<^._

object RolloutPage extends TopLevelPage {

  override val name: String = i18n.tr("Rollout")

  override val routerPath: Path = Path("#rollout")

  override val content: VdomElement = <.div(RolloutComponent.Component())

}
