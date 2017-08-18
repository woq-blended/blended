package blended.mgmt.ui.util

import blended.mgmt.ui.components.wrapper.ReactSplitPane
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
      case Some(sc) => ReactSplitPane.Component(
        ReactSplitPane.props(
          allowResize = true,
          minSize = 100,
          defaultSize = 100,
          paneStyle = autoOverflow,
          split = if (verticalFirst) "horizontal" else "vertical"
        )
      )(sc, mainComponent)
    }

    firstComponent match {
      case None => secondary
      case Some(fc) => ReactSplitPane.Component(
        ReactSplitPane.props(
          allowResize = true,
          minSize = 100,
          defaultSize = 100,
          paneStyle = autoOverflow,
          split = if (verticalFirst) "vertical" else "horizontal"
        )
      )(fc, secondary)
    }

  }
}
