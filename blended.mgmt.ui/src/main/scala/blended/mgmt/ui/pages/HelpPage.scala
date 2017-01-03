package blended.mgmt.ui.pages

import japgolly.scalajs.react.{ReactComponentB, _}
import japgolly.scalajs.react.vdom.prefix_<^._

object HelpPage {

  val component = ReactComponentB.static( "Help",
    <.p(
      "This is the help Page"
    )
  ).build
}
