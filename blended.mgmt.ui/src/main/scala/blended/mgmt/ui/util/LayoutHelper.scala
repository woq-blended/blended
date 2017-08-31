package blended.mgmt.ui.util

import chandu0101.scalajs.react.components.reactsplitpane.ReactSplitPane
import japgolly.scalajs.react.vdom.html_<^._

import scala.scalajs.js

object LayoutHelper {

  def splitLayout(
    main: VdomElement,
    firstComponent: Option[VdomElement] = None,
    secondComponent: Option[VdomElement] = None,
    verticalFirst: Boolean = true
  ): VdomElement = {

    val autoOverflow = js.Dynamic.literal("overflow" -> "auto")

    val mainComponent : VdomElement = <.div(
      ^.cls := "splitPaneContent",
      main
    )

    val secondary: VdomElement = secondComponent match {
      case None => mainComponent
      case Some(sc) => ReactSplitPane(
        allowResize = true,
        minSize = 100.0,
        defaultSize = 100.0,
        paneStyle = autoOverflow,
        split = if (verticalFirst) "horizontal" else "vertical"
      )(sc, mainComponent)
    }

    firstComponent match {
      case None => secondary
      case Some(fc) => ReactSplitPane(
        allowResize = true,
        minSize = 100.0,
        defaultSize = 100.0,
        paneStyle = autoOverflow,
        split = if (verticalFirst) "vertical" else "horizontal"
      )(fc, secondary)
    }

  }
}
