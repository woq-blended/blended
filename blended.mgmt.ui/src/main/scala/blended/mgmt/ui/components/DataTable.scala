package blended.mgmt.ui.components

import blended.mgmt.ui.styles.PanelDefault
import blended.mgmt.ui.util.I18n
import chandu0101.scalajs.react.components.reacttable.ReactTable
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

import scalacss.ScalaCssReact._
import scalacss.internal.mutable.GlobalRegistry

object DataTable {

  def fromStringSeq(
    panelHeading : Option[String],
    content : Seq[Seq[String]],
    headings : Seq[(String, Int)],
    selectable : Boolean = false,
    multiSelectable : Boolean = false,
    allSelectable : Boolean = false
  ) : VdomElement = {

    val i18n = I18n()
    val panelStyle = GlobalRegistry[PanelDefault.type].get

    val width = 100.0 / headings.size

    ContentPanel(panelHeading)(
      ReactTable(
        data = content,
        configs = headings.map{ h =>
          ReactTable.SimpleStringConfig[Seq[String]](
            name = i18n.tr(h._1),
            _(h._2),
            width = Some(s"$width%")
          )
        },
        paging = false,
        selectable = selectable,
        multiSelectable = multiSelectable,
        allSelectable = allSelectable
      )()
    )
  }

  def fromProperties(
    panelHeading: Option[String],
    content: Map[String, String],
    headings : (String, String) = ("name", "value")
  ) : VdomElement = {

    fromStringSeq(
      panelHeading = panelHeading,
      content = content.map { case (k,v) => Seq(k,v) }.toSeq,
      headings = Seq((headings._1, 0), (headings._2, 1))
    )
  }
}
