package blended.mgmt.ui.styles

import scalacss.DevDefaults._

object BootstrapStyles extends StyleSheet.Inline {

  import Colors._
  import dsl._

  val navbarHeight = 61.px

  val header = style("header") (
    height(navbarHeight)
  )

  val row = style("row") (
    margin(0.px)
  )

  val baseView = style (
    width(100.%%),
    position.absolute,
    left(0.px),
    overflow.auto
  )

  val loginView = style("login")(
    baseView,
    top.`0`,
    height(100.%%)
  )

  val viewport = style("viewport") (
    baseView,
    top(navbarHeight),
    height := s"calc(100vh - ${navbarHeight.value})"
  )

  val ulNavbarHeader = style("ul.navbar-header") (
    marginBottom(0.px)
  )

  val navbar = style("navbar") (
    backgroundColor(c"#eee"),
    marginBottom(0.px)
  )

  val navbarNav = style("navbar-nav") (
    border.none
  )

  val navbarNavLi = style("navbar-nav li") {
    listStyleType := "none"
  }


  val navbarBlended = style("navbar-blended") (
    &.hover (
      textDecoration := "underline"
    ),
    cursor.pointer,
    unsafeChild("a") (
      textDecoration := "none",
      color(black)
    )
  )

  val navbarSelected = style("navbar-selected") (
    navbarBlended,
    borderRadius(10.px),
    backgroundColor(brandPrimary),
    unsafeChild("a") (
      color(white)
    )
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
