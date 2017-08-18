package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import vdom.html_<^._
import japgolly.scalajs.react.extra.router.Path


object SettingsPage extends TopLevelPage {

  override val name: String = i18n.tr("Settings")

  override val routerPath: Path = Path("#settings")

  override val content: VdomElement = <.p(
    i18n.tr("This is the settings Page")
  )
}
