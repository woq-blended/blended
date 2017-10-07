package blended.mgmt.ui.util

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

object FormHelper {

  val i18n = I18n()

  def input(
    id : String,
    label : String,
    inputType : String,
    value : String,
    lblWidth : String = "100px",
    changeCallback : (ReactEventFromTextArea => Callback)
  ) : VdomElement = {
    <.div(
      ^.cls := "form-group row",
      ^.marginBottom := "1em",
      ^.display := "flex",
      ^.flexDirection.row,
      <.label(
        ^.display := "flex",
        ^.flex := lblWidth,
        ^.alignItems.center,
        ^.`for` := id,
        ^.cls := "col-form-label",
        i18n.tr(label)
      ),
      <.input(
        ^.display := "flex",
        ^.alignItems.center,
        ^.id := id,
        ^.`type` := inputType,
        ^.cls := "form-control",
        ^.value := value,
        ^.onChange ==> changeCallback
      )
    )
  }

}
