package blended.mgmt.ui.components.wrapper

import japgolly.scalajs.react._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object ReactSplitPane {

  @js.native
  @JSImport("react-split-pane", JSImport.Default)
  object RawSplitPane extends js.Object

  @js.native
  trait Props extends js.Object {
    var allowResize : Boolean = js.native
    var minSize : Double = js.native
    var defaultSize : Double = js.native
    var size : Double = js.native
  }

  def props(
    allowResize : Boolean,
    minSize : Double,
    defaultSize : Double
  ) : Props = {
    val p = (new js.Object).asInstanceOf[Props]

    p.allowResize = allowResize
    p.minSize = minSize
    p.defaultSize = defaultSize
    p.size = defaultSize

    p
  }

  val Component = JsComponent[Props, Children.Varargs, Null](RawSplitPane)
}
