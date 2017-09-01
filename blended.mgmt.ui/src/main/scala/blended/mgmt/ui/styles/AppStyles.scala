package blended.mgmt.ui.styles

import scalacss.DevDefaults._
import scalacss.ScalaCssReact._
import scalacss.internal.mutable.GlobalRegistry
import chandu0101.scalajs.react.components.ReactTable
import chandu0101.scalajs.react.components.reactsplitpane.ReactSplitPane

object PanelDefault extends StyleSheet.Inline {

  import dsl._

  val container = style(
    addClassNames("panel", "panel-default")
  )
}

object AppStyles {

  def load() : Unit = {

    GlobalRegistry.register(
      PanelDefault,
      ReactTable.DefaultStyle,
      ReactSplitPane.DefaultStyle,
      BootstrapStyles
    )

    GlobalRegistry.addToDocumentOnRegistration()
  }

}