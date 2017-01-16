package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._

object ContainerPage extends TopLevelPage {

  override val name: String = i18n.tr("Container")

  override val routerPath: Path = Path.root

  override val content: ReactElement =
    <.p(
      i18n.tr("This is the container Page")
    )

}
