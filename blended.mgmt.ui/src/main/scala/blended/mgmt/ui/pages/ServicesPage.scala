package blended.mgmt.ui.pages

import blended.mgmt.ui.components.{DataTableComp, DataTableContent}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.prefix_<^._

object ServicesPage extends TopLevelPage {

  override val name: String = i18n.tr("Services")

  override val routerPath: Path = Path("#services")

  val dtContent = DataTableContent(
    title = "My cool DataTable",
    headings = Array("Prop 1", "Prop 2", "Prop 3"),
    content = List(
      Array("P1", "P2", "P3"),
      Array("P4", "P5", "P6")
    )
  )

  override val content: ReactElement =
    <.div(DataTableComp.Component(dtContent))
}
