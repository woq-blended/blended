package blended.mgmt.ui.styles

import scalacss.DevDefaults._

object BootstrapStyles extends StyleSheet.Inline {

  import dsl._

  // TODO: Can we get default values from Bootstrap less somehow ?
  val brandPrimary = c"#337ab7"
  val navbarHeight = 61.px;

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
    top(navbarHeight),
    overflow.auto
  )

  val ulNavbarHeader = style("ul.navbar-header") (
    marginBottom(0.px)
  )

  val navbar = style("navbar") (
    backgroundColor(c"#eee"),
    marginBottom(0.px)
  )

  val navbarNav = style("navbar-nav") (
    border.none,
    unsafeChild("li") {
      listStyleType := "none"
    }
  )

  val navbarBlended = style("navbar-blended") (
    textDecoration := "none",
    &.hover (
      textDecoration := "underline"
    ),
    cursor.pointer
  )

  val navbarSelected = style("navbar-selected") (
    navbarBlended,
    backgroundColor(brandPrimary),
    color(white),
    borderRadius(10.px)
  )

  val panelDefault = style("panel-default") (
    borderWidth(1.px),
    borderTopLeftRadius(1.em),
    borderTopRightRadius(1.em),
    margin(1.em)
  )

  val panelHeading = style("panel-heading") (
    borderTopLeftRadius(1.em),
    borderTopRightRadius(1.em)
  )
}
