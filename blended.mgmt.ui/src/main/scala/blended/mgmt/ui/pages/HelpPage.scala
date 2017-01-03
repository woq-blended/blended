package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._

object HelpPage extends TopLevelPage {

  override val name: String = "Help"

  override val routerPath: Path = Path("#help")

  override val content: ReactElement =
    <.p(
      "This is the help Page"
    )
}
