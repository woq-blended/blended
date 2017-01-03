package blended.mgmt.ui

import blended.mgmt.ui.pages.{ContainerPage, HelpPage}
import org.scalajs.dom
import japgolly.scalajs.react._, vdom.prefix_<^._
import japgolly.scalajs.react.extra._
import japgolly.scalajs.react.extra.router._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

@JSExport
class MgmtConsole extends js.JSApp {

  sealed trait Page
  case object Container extends Page
  case object Help extends Page

  val routerConfig = RouterConfigDsl[Page].buildConfig { dsl =>
    import dsl._

    (trimSlashes
    | staticRoute(root, Container) ~> render(ContainerPage.component())
    | staticRoute("#help", Help) ~> render(HelpPage.component())
    )
      .notFound(redirectToPage(Container)(Redirect.Replace))
      .renderWith(layout(_,_))

  }

  def layout(c: RouterCtl[Page], r: Resolution[Page]) =
    <.div(
      navMenu(c),
      <.div(
        ^.cls := "container", r.render()
      )
    )

  val navMenu = ReactComponentB[RouterCtl[Page]]("Menu")
    .render_P { ctl =>

      def nav(name: String, target: Page) =
        <.li(
          ^.cls := "navbar-brand active",
          ctl.setOnClick(target),
          name
        )

      <.div(
        ^.cls := "navbar navbar-default",
        <.ul(
          ^.cls := "navbar-header",
          nav("Container", Container),
          nav("Help", Help)
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
