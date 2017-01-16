package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.components.CompManagementConsole

object ContainerPage extends TopLevelPage {

  override val name: String = i18n.tr("Container")

  override val routerPath: Path = Path.root

  override val content: ReactElement =
    <.div(CompManagementConsole.CompManagementConsole())

}
