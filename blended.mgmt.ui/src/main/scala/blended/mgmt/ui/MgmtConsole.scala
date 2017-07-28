package blended.mgmt.ui

import blended.mgmt.ui.backend.DataManager
import blended.mgmt.ui.pages._
import org.scalajs.dom
import japgolly.scalajs.react._
import vdom.prefix_<^._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.extra.router._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

@JSExport
class MgmtConsole extends js.JSApp {

  case class NavState(
    selected : TopLevelPage
  )

  class Backend(scope: BackendScope[RouterCtl[TopLevelPage], NavState]) {

    def render(ctl : RouterCtl[TopLevelPage], state : NavState) = {

      def nav(name: String, target: TopLevelPage) = {

        // TODO : why does page selection not propagate into menu ?
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
          TopLevelPages.values.map { tlp =>
            nav(tlp.name, tlp)
          }
        )
      )
    }
  }

  val initial = NavState(TopLevelPages.defaultPage)

  val navMenu = ReactComponentB[RouterCtl[TopLevelPage]]("Menu")
    .initialState(initial)
    .backend(new Backend(_))
    .renderBackend
    .build

  val routerConfig = RouterConfigDsl[TopLevelPage].buildConfig { dsl =>
    import dsl._

    (trimSlashes | TopLevelPages.routes)
      .notFound(redirectToPage(TopLevelPages.defaultPage)(Redirect.Replace))
      .renderWith(layout(_, _))
  }

  def layout(c: RouterCtl[TopLevelPage], r: Resolution[TopLevelPage]) =
    <.div(
      navMenu(c),
      <.div(
        ^.cls := "container-fluid", r.render()
      )
    )

  val baseUrl =
      BaseUrl.fromWindowOrigin / "management/"

  override def main(): Unit = {
    val router = Router(baseUrl, routerConfig.logToConsole)

    router().render(dom.document.body)
  }
}
