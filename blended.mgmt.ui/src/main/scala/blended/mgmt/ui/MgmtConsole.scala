package blended.mgmt.ui

import blended.mgmt.ui.components.wrapper.ReactSplitPane
import blended.mgmt.ui.pages.TopLevelPages.values
import blended.mgmt.ui.pages._
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

        val highlight : String = target.equals(state.selected) match {
          case true => " navbar-selected"
          case _ => ""
        }

        <.li(
          ^.cls := "navbar-brand " + highlight,
          ^.onClick --> scope.modState{ s =>
              ctl.set(target).runNow()
              s.copy(selected = target)
            },
          name
        )
      }

      <.div(
        ^.cls := "navbar navbar-default",
        <.ul(
          ^.cls := "navbar-header",
          TagMod(TopLevelPages.values.map { tlp =>
            nav(tlp.name, tlp)
          }:_*)
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

  def layout(c: RouterCtl[TopLevelPage], r: Resolution[TopLevelPage]) : VdomElement =
    <.div(
      navMenu(c),
      ReactSplitPane.Component(
        ReactSplitPane.props(
          allowResize = true,
          minSize = 100,
          defaultSize = 100
        )
      )(
        <.div("first panel"),
        <.div(
          ^.cls := "container-fluid", r.render()
        )
      )
    )

  val baseUrl =
      BaseUrl.fromWindowOrigin / "management/"

  def main(): Unit = {
    val router = Router(baseUrl, routerConfig.logToConsole)

    router().renderIntoDOM(dom.document.getElementById("content"))
  }
}
