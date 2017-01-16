package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._

object HelpPage extends TopLevelPage {

  override val name: String = i18n.tr("Help")

  override val routerPath: Path = Path("#help")

  override val content: ReactElement =
    <.p(
      i18n.tr("This is the help Page")
    )
}
