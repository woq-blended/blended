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

    var split : String = js.native

    var style : js.Object = js.native
    var paneStyle : js.Object = js.native
    var pane1Style : js.Object = js.native
    var pane2Style : js.Object = js.native
    var resizerStyle : js.Object = js.native
  }

  def props(
    allowResize : Boolean = true,
    minSize : Double = 100,
    defaultSize : Double = 100,
    split : String = "vertical",
    style : js.Object = new js.Object(),
    paneStyle : js.Object = new js.Object(),
    paneStyle1 : js.Object = new js.Object(),
    paneStyle2 : js.Object = new js.Object(),
    resizerStyle : js.Object = new js.Object()
  ) : Props = {
    val p = (new js.Object).asInstanceOf[Props]

    p.allowResize = allowResize
    p.minSize = minSize
    p.defaultSize = defaultSize
    p.size = defaultSize

    p.split = split

    p.style = style
    p.paneStyle = paneStyle
    p.pane1Style = paneStyle1
    p.pane2Style = paneStyle2
    p.resizerStyle = resizerStyle

    p
  }

  val Component = JsComponent[Props, Children.Varargs, Null](RawSplitPane)
}
