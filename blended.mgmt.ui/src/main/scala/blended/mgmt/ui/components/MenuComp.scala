package blended.mgmt.ui.components

import blended.mgmt.ui.routes.{MgmtPage, MgmtRouter}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.{Path, Resolution, RouterCtl}
import japgolly.scalajs.react.vdom.html_<^._


object MenuComp {

  case class MenuCompProps(
    ctl : RouterCtl[MgmtPage],
    res : Resolution[MgmtPage]
  )

  class Backend(scope: BackendScope[MenuCompProps, Unit]) {

    def render(p : MenuCompProps) : VdomElement = {

      val currentPath : Path = p.res.page.routeInfo.routerPath

      def nav(name: String, target: MgmtPage) = {

        val cls: String = (currentPath == target.routeInfo.routerPath) match {
          case true => "navbar-selected navbar-brand"
          case _ => "navbar-blended navbar-brand"
        }

        <.li(
          ^.cls := cls,
          p.ctl.link(target)(target.routeInfo.name)
        )
      }

      <.header(
        <.nav(
          ^.cls := "navbar navbar-fixed-top",
          ^.margin := "5px",
          ^.borderRadius := "5px",
          <.ul(
            ^.cls := "navbar navbar-nav",
            TagMod(MgmtRouter.pages.map { p =>
              nav(p.routeInfo.name, p)
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

  def apply(ctl : RouterCtl[MgmtPage], res : Resolution[MgmtPage]) = navMenu(MenuCompProps(ctl, res))

}