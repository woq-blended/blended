package blended.mgmt.ui.pages

import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.html_<^._

object RolloutPage extends TopLevelPage {

  override val name: String = i18n.tr("Rollout")

  override val routerPath: Path = Path("#rollout")

  override val content: VdomElement = <.p(
    i18n.tr("This is the rollout Page")
  )
}
