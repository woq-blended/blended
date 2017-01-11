package blended.mgmt.ui.pages

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._

object ServicesPage extends TopLevelPage {

  override val name: String = "Services"

  override val routerPath: Path = Path("#services")

  override val content: ReactElement = <.p(
    "This is the services Page"
  )
}
