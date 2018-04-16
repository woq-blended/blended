package blended.mgmt.app

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object SampleComponent {

  case class Props(name : String)

  def apply(name : String) = myComponent(Props(name))

  private[this] lazy val myComponent = ScalaComponent.builder[Props]("MyComponent")
    .render_P(p => <.div(p.name))
    .build
}
