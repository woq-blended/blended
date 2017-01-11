package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._

object ProfilesPage extends TopLevelPage {

  override val name: String = "Profiles"

  override val routerPath: Path = Path("#profiles")

  override val content: ReactElement = <.p(
    "This is the profiles Page"
  )
}
