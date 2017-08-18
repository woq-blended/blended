package blended.mgmt.ui.pages

import blended.mgmt.ui.components.ContainerComp
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.html_<^._

object ContainerPage extends TopLevelPage {

  override val name: String = i18n.tr("Container")

  override val routerPath: Path = Path.root

  override val verticalFirst = true

  override val firstComponent : Option[VdomElement] = Some(<.div("first panel"))

  override val secondComponent : Option[VdomElement] = Some(<.div("second panel"))

  override val mainContent: VdomElement =
    <.div(ContainerComp.Component())

}
