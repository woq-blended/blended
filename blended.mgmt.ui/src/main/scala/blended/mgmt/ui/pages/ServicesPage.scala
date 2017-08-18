package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import vdom.html_<^._
import blended.mgmt.ui.components.ServicesComp
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.VdomElement

object ServicesPage extends TopLevelPage {

  override val name: String = i18n.tr("Services")

  override val routerPath: Path = Path("#services")

  override val content: VdomElement = ServicesComp.Component()
}
