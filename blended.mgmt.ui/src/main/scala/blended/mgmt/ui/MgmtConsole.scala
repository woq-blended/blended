package blended.mgmt.ui

import blended.mgmt.ui.pages.TopLevelPages.values
import blended.mgmt.ui.pages._
import blended.mgmt.ui.styles.AppStyles
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom

import scala.scalajs.js

object MgmtConsole extends js.JSApp {

  case class NavState(
    selected : TopLevelPage
  )

  class Backend(scope: BackendScope[RouterCtl[TopLevelPage], NavState]) {

    def render(ctl : RouterCtl[TopLevelPage], state : NavState) = {

      def nav(name: String, target: TopLevelPage) = {

        val cls : String = target.equals(state.selected) match {
          case true => "navbar-selected navbar-brand"
          case _ => "navbar-blended navbar-brand"
        }

        <.li(
          ^.cls := cls,
          ^.onClick --> scope.modState{ s =>
              ctl.set(target).runNow()
              s.copy(selected = target)
            },
          name
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
            }:_*)
          )
        )
      )
    }
  }

  val initial = NavState(TopLevelPages.defaultPage)

  val navMenu = ScalaComponent.builder[RouterCtl[TopLevelPage]]("Menu")
    .initialState(initial)
    .backend(new Backend(_))
    .renderBackend
    .build

  val routes = RouterConfigDsl[TopLevelPage].buildRule { dsl =>
    import dsl._

    values.foldLeft(trimSlashes){ (rule, page) =>
      rule | staticRoute(page.routerPath, page) ~> renderR(_ => page.component())
    }
  }

  val routerConfig = RouterConfigDsl[TopLevelPage].buildConfig { dsl =>
    import dsl._

    (trimSlashes | routes)
      .notFound(redirectToPage(TopLevelPages.defaultPage)(Redirect.Replace))
      .renderWith(layout(_, _))
  }

  def layout(c: RouterCtl[TopLevelPage], r: Resolution[TopLevelPage]) : VdomElement = {

    <.section(
      ^.height := "100vh",
      navMenu(c),
      <.div( ^.cls := "viewport", r.render())
    )
  }


  val baseUrl =
      BaseUrl.fromWindowOrigin / "management/"

  def main(): Unit = {

    AppStyles.load()

    val router = Router(baseUrl, routerConfig.logToConsole)

    router().renderIntoDOM(dom.document.getElementById("content"))
  }
}
