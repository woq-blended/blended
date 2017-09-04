package blended.mgmt.ui.components

import blended.mgmt.ui.styles.PanelDefault
import blended.mgmt.ui.util.I18n
import chandu0101.scalajs.react.components.ReactTable
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

import scalacss.ScalaCssReact._
import scalacss.internal.mutable.GlobalRegistry

object PropertyTable {

  def apply(heading: String, content: Map[String, String], headings : (String, String) = ("name", "value")) : VdomElement = {

    val i18n = I18n()
    val panelStyle = GlobalRegistry[PanelDefault.type].get

    <.div(
      panelStyle.container,
      <.div(
        ^.cls := "panel-heading",
        <.h2(i18n.tr(heading))
      ),
      ReactTable(
        data = content.toSeq,
        configs = List(
          ReactTable.SimpleStringConfig[(String, String)](i18n.tr(headings._1), _._1, width = Some("50%")),
          ReactTable.SimpleStringConfig[(String, String)](i18n.tr(headings._2), _._2, width = Some("50%"))
        )
      )()
    )

  }
}
