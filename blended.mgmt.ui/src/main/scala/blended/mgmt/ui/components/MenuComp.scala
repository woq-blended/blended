package blended.mgmt.ui.components

import blended.mgmt.ui.backend.LoginManager
import blended.mgmt.ui.routes.{MgmtPage, MgmtRouter}
import blended.mgmt.ui.util.{I18n, Logger}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.{Path, Resolution, RouterCtl}
import japgolly.scalajs.react.vdom.html_<^._


object MenuComp {

  private[this] val log = Logger[MenuComp.type]
  private[this] val i18n = I18n()

  case class MenuCompProps(
    ctl : RouterCtl[MgmtPage],
    res : Resolution[MgmtPage]
  )

  class Backend(scope: BackendScope[MenuCompProps, Unit]) {

    def logout(p: MenuCompProps) : ReactEvent => Callback = { e =>
      LoginManager.logout()
      p.ctl.set(MgmtRouter.pages.head)
    }

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
        ).when(!target.routeInfo.loginRequired || LoginManager.loggedIn)
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
            }: _*),
            <.li(
              ^.cls := "navbar-blended navbar-brand",
              ^.onClick ==> logout(p),
              i18n.tr("Logout")
            ).when(LoginManager.loggedIn)
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