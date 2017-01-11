package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._

object OverlaysPage extends TopLevelPage {

  override val name: String = "Overlays"

  override val routerPath: Path = Path("#overlays")

  override val content: ReactElement = <.p(
    "This is the overlay page "
  )
}
