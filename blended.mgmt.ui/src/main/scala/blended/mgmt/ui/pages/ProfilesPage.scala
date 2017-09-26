package blended.mgmt.ui.pages

import japgolly.scalajs.react.vdom.html_<^._
import blended.mgmt.ui.components.ProfilesComp
import japgolly.scalajs.react.extra.router.Path

object ProfilesPage extends TopLevelPage {

  override val name: String = i18n.tr("Profiles")

  override val routerPath: Path = Path("#profiles")

  override val content : VdomElement = ProfilesComp()()
}
