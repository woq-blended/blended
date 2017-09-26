package blended.mgmt.ui.components

import blended.mgmt.ui.pages.{TopLevelPage, TopLevelPages}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.{Path, Resolution, RouterCtl}
import japgolly.scalajs.react.vdom.html_<^._


object MenuComp {

  case class MenuCompProps(
    ctl : RouterCtl[TopLevelPage],
    res : Resolution[TopLevelPage]
  )

  class Backend(scope: BackendScope[MenuCompProps, Unit]) {

    def render(p : MenuCompProps) : VdomElement = {

      val currentPath : Path = p.res.page.routerPath

      def nav(name: String, target: TopLevelPage) = {

        val cls: String = (currentPath == target.routerPath) match {
          case true => "navbar-selected navbar-brand"
          case _ => "navbar-blended navbar-brand"
        }

        <.li(
          ^.cls := cls,
          <.a(
            ^.href := target.routerPath.value,
            name
          )
        )
      }

      <.header(
        <.nav(
          ^.cls := "navbar navbar-fixed-top",
          ^.margin := "5px",
          ^.borderRadius := "5px",
          <.ul(
            ^.cls := "navbar navbar-nav",
            TagMod(TopLevelPages.values.map { tlp =>
              nav(tlp.name, tlp)
            }: _*
            )
          )
        )
      )
    }
  }

  val navMenu = ScalaComponent.builder[MenuCompProps]("Menu")
    .renderBackend[Backend]
    .build

  def apply(ctl : RouterCtl[TopLevelPage], res : Resolution[TopLevelPage]) = navMenu(MenuCompProps(ctl, res))

}