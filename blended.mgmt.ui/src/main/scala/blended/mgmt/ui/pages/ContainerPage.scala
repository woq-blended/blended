package blended.mgmt.ui.pages

import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.html_<^._
import blended.mgmt.ui.components.ContainerComp

object ContainerPage extends TopLevelPage {

  override val name: String = i18n.tr("Container")

  override val routerPath: Path = Path.root

  override val content: VdomElement =
    <.div(ContainerComp.Component())

}
