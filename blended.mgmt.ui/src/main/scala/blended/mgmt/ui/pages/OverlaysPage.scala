package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.components.OverlaysComp

object OverlaysPage extends TopLevelPage {

  override val name: String = i18n.tr("Overlays")

  override val routerPath: Path = Path("#overlays")

  override val content: ReactElement = <.p(
    OverlaysComp.Component()
  )
}
