package blended.mgmt.ui.styles

import scalacss.DevDefaults._


object PanelDefault extends StyleSheet.Inline {

  import dsl._

  val container = style(
    addClassNames("panel", "panel-default")
  )
}


