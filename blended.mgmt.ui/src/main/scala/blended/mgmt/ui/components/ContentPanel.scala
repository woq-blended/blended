package blended.mgmt.ui.components

import blended.mgmt.ui.styles.PanelDefault
import blended.mgmt.ui.util.I18n
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

import scalacss.ScalaCssReact._
import scalacss.internal.mutable.GlobalRegistry

object ContentPanel {

  val i18n = I18n()

  def apply(
    panelHeading : String
  )(content : TagMod) : VdomElement = {

    val panelStyle = GlobalRegistry[PanelDefault.type].get

    <.div(
      panelStyle.container,
      <.div(
        ^.cls := "panel-heading",
        <.h2(i18n.tr(panelHeading))
      ),
      <.div(
        ^.cls := "panel-body",
        content
      )
    )
  }

}
