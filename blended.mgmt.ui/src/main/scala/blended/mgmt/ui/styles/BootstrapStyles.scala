package blended.mgmt.ui.styles

import scalacss.DevDefaults._

object BootstrapStyles extends StyleSheet.Inline {

  import dsl._

  val navbarHeight = 50.px;

  val header = style("header") (
    height(navbarHeight)
  )

  val row = style("row") (
    margin(0.px)
  )

  val viewport = style("viewport") (
    height := s"calc(100vh - ${navbarHeight.value})",
    width(100.%%),
    position.absolute,
    left(0.px),
    top(navbarHeight)
  )

//  val ulNavbarHeader = style("ul.navbar-header") (
//    marginBottom(0.px)
//  )
//
//  val navbar = style("navbar") (
//    marginBottom(0.px)
//  )
//
  val navbarNav = style("navbar-nav") (
    border.none,
    unsafeChild("li") {
      listStyleType := "none"
    }
  )
}
