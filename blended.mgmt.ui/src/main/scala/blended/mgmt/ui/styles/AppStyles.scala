package blended.mgmt.ui.styles

import scalacss.DevDefaults._

object AppStyles extends StyleSheet.Inline {

  import dsl._

  val panelDefault = style(
    addClassNames("panel", "panel-default")
  )


}
