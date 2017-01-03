package blended.mgmt.ui

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
        ^.cls := "container", r.render()
      )
    )

  val navMenu = ReactComponentB[RouterCtl[TopLevelPage]]("Menu")
    .render_P { ctl =>

      def nav(name: String, target: TopLevelPage) =
        <.li(
          ^.cls := "navbar-brand active",
          ctl.setOnClick(target),
          name
        )

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
    .configure(Reusability.shouldComponentUpdate)
    .build

  val baseUrl =
      BaseUrl.fromWindowOrigin / "management/"

  override def main(): Unit = {
    val router = Router(baseUrl, routerConfig.logToConsole)
    router().render(dom.document.body)
  }
}
