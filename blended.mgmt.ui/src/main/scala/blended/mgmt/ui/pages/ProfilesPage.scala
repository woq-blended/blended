package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.components.ProfilesComp

object ProfilesPage extends TopLevelPage {

  override val name: String = i18n.tr("Profiles")

  override val routerPath: Path = Path("#profiles")

  override val content: ReactElement = <.p(
    <.div(ProfilesComp.Component())
  )
}
