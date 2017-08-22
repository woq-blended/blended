package blended.mgmt.ui.components

import blended.mgmt.ui.styles.PanelDefault
import blended.mgmt.ui.util.{I18n, Logger}
import chandu0101.scalajs.react.components.ReactTable
import japgolly.scalajs.react.{BackendScope, ScalaComponent}
import japgolly.scalajs.react.vdom.html_<^._

import scalacss.internal.mutable.GlobalRegistry
import scalacss.ScalaCssReact._

object DataTableContent {
  def apply(title : String, content: Map[String, String]) : DataTableContent = {

    val tContent = content.map{ case (k,v) => Map("name" -> k, "value" -> v) }.toVector

    DataTableContent(
      title = title,
      headings = List("name", "value"),
      tContent
    )
  }
}

case class DataTableContent(
  title : String,
  headings : List[String],
  content : Vector[ReactTable.Model]
)

object DataTableComp {

  private[this] val log = Logger[DataTableComp.type]
  private[this] val i18n = I18n()

  class Backend(scope: BackendScope[DataTableContent, Unit]) {

    def render(props: DataTableContent) = {

      val width = 100 / props.headings.size
      val panelStyle = GlobalRegistry[PanelDefault.type].get

      <.div(
        panelStyle.container,
        <.div(
          ^.cls := "panel-heading",
          <.h2(i18n.tr(props.title))
        ),
        ReactTable(
          data = props.content,
          columns = props.headings
        )
      )
    }
  }

  val Component = ScalaComponent.builder[DataTableContent]("DataTable")
    .renderBackend[Backend]
    .build
}
