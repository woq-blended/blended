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
    changeCallback : (ReactEventFromTextArea => Callback)
  ) : VdomElement = {
    <.div(
      ^.cls := "form-group row",
      <.label(
        ^.`for` := id,
        ^.cls := "col-sm-2 col-form-label",
        i18n.tr(label)
      ),
      <.div(
        ^.cls := "col-sm-10",
        <.input(
          ^.id := id,
          ^.`type` := inputType,
          ^.cls := "form-control",
          ^.value := value,
          ^.onChange ==> changeCallback
        )
      )
    )
  }

}
