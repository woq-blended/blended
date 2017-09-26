package blended.mgmt.ui

import blended.mgmt.ui.backend.LoginManager
import blended.mgmt.ui.components.MenuComp
import blended.mgmt.ui.pages.{LoginPage, TopLevelPage, TopLevelPages}
import japgolly.scalajs.react.CallbackTo
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._

object MgmtRouter {

  // We define a condition to determine whether someone has logged in already
  val accessGranted : TopLevelPage => CallbackTo[Boolean] = p => CallbackTo[Boolean] {
    !p.requiresLogin || LoginManager.loggedIn
  }

  // If no user is currently logged, we define an Action to redirect the user to
  // a login page

  val login : Action[TopLevelPage] = RedirectToPage[TopLevelPage](LoginPage, Redirect.Replace)
  val performLogin : TopLevelPage => Option[Action[TopLevelPage]] = _  => Some(login)

  val routes = RouterConfigDsl[TopLevelPage].buildRule { dsl =>
    import dsl._

    TopLevelPages.values.foldLeft(trimSlashes){ (rule, page) =>
      rule | staticRoute(page.routerPath, page) ~> render(page.content)
    }
  }

  val routerConfig = RouterConfigDsl[TopLevelPage].buildConfig { dsl =>

    import dsl._

    (routes | staticRoute(LoginPage.routerPath, LoginPage) ~> render(LoginPage.content))
      .addCondition(accessGranted)(performLogin)
      .notFound(redirectToPage(TopLevelPages.defaultPage)(Redirect.Replace))
      .renderWith(layout(_, _))
  }

  def layout(c: RouterCtl[TopLevelPage], r: Resolution[TopLevelPage]) : VdomElement  = {

    val isLoginPage = r.page.routerPath == LoginPage.routerPath

    <.section(
      ^.height := "100vh",
      MenuComp(c,r)().unless(isLoginPage),
      <.div(
        (^.cls := "viewport").unless(isLoginPage),
        (^.cls := "login").when(isLoginPage),
        r.render()
      )
    )
  }

}
