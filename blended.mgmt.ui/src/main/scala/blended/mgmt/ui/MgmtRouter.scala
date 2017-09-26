package blended.mgmt.ui

import blended.mgmt.ui.components.MenuComp
import blended.mgmt.ui.pages.{TopLevelPage, TopLevelPages}
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.VdomElement
import japgolly.scalajs.react.vdom.html_<^._

object MgmtRouter {

  val routes = RouterConfigDsl[TopLevelPage].buildRule { dsl =>
    import dsl._

    TopLevelPages.values.foldLeft(trimSlashes){ (rule, page) =>
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
      MenuComp(c)(),
      <.div( ^.cls := "viewport", r.render())
    )
  }

}
