package blended.mgmt.ui.components

import blended.mgmt.ui.pages.{TopLevelPage, TopLevelPages}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._

import scala.scalajs.js

object MenuComp {

  class Backend(scope: BackendScope[RouterCtl[TopLevelPage], Unit]) {

    def render(ctl: RouterCtl[TopLevelPage]) = {

      val currentUrl: String = js.Dynamic.global.window.location.href.toString

      def nav(name: String, target: TopLevelPage) = {

        val cls: String = currentUrl.endsWith(target.routerPath.value) match {
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

  val navMenu = ScalaComponent.builder[RouterCtl[TopLevelPage]]("Menu")
    .renderBackend[Backend]
    .build

  def apply(ctl : RouterCtl[TopLevelPage]) = navMenu(ctl)

}