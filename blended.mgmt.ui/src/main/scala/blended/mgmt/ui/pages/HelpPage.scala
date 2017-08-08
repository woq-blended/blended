package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import vdom.html_<^._
import japgolly.scalajs.react.extra.router.Path

object HelpPage extends TopLevelPage {

  override val name: String = i18n.tr("Help")

  override val routerPath: Path = Path("#help")

  override val content: VdomElement = <.p(
    i18n.tr("This is the help Page")
  )
}
