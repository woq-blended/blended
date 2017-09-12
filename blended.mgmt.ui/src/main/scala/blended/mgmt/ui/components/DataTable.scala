package blended.mgmt.ui.components

import blended.mgmt.ui.styles.PanelDefault
import blended.mgmt.ui.util.I18n
import chandu0101.scalajs.react.components.reacttable.ReactTable
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

import scalacss.ScalaCssReact._
import scalacss.internal.mutable.GlobalRegistry

object DataTable {

  def apply(
    panelHeading : String,
    content : Seq[Seq[String]],
    headings : Seq[(String, Int)]
  ) : VdomElement = {

    val i18n = I18n()
    val panelStyle = GlobalRegistry[PanelDefault.type].get

    val width = 100.0 / headings.size

    <.div(
      panelStyle.container,
      <.div(
        ^.cls := "panel-heading",
        <.h2(i18n.tr(panelHeading))
      ),
      ReactTable(
        data = content,
        configs = headings.map{ h =>
          ReactTable.SimpleStringConfig[Seq[String]](
            name = i18n.tr(h._1),
            _(h._2),
            width = Some(s"$width")
          )
        },
        paging = false
      )()
    )
  }

  def apply(
    panelHeading: String,
    content: Map[String, String],
    headings : (String, String) = ("name", "value")
  ) : VdomElement = {

    apply(
      panelHeading = panelHeading,
      content = content.map { case (k,v) => Seq(k,v) }.toSeq,
      headings = Seq((headings._1, 0), (headings._2, 1))
    )

  }
}
