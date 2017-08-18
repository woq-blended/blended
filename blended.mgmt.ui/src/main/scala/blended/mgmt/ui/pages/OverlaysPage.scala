package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import vdom.html_<^._
import japgolly.scalajs.react.extra.router.Path
import blended.mgmt.ui.components.OverlayConfigComp

object OverlaysPage extends TopLevelPage {

  override val name: String = i18n.tr("Overlays")

  override val routerPath: Path = Path("#overlays")

  override val mainContent: VdomElement = OverlayConfigComp.Component()
}
