package blended.mgmt.ui.routes

import blended.mgmt.ui.backend.{LoginManager, RolloutProfileAction}
import blended.mgmt.ui.components._
import blended.mgmt.ui.util.Logger
import japgolly.scalajs.react.CallbackTo
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._

object MgmtRouter {

  private[this] val logger = Logger[MgmtRouter.type]

  private[this] val loginRouterPath = "#login"

  private[this] val ctPage = MgmtPage(
    name = "Container",
    routerPath = "#containers",
    content = ContainerComp(_)
  )

  private[this] val svcPage = MgmtPage(
    name = "Services",
    routerPath = "#services",
    content = ServicesComp(_)
  )

  private[this] val profPage = MgmtPage(
    name = "Profiles",
    routerPath = "#profiles",
    content = ProfilesComp(_)
  )

  private[this] val overlaypage = MgmtPage(
    name = "Overlays",
    routerPath = "#overlays",
    content = OverlayConfigComp(_)
  )

  private[this] val rolloutPage = MgmtPage(
    name = "Rollout",
    routerPath = "#rollout",
    content = RolloutComponent(_, Some(RolloutProfileAction.DefaultAjax))
  )

  private[this] val settingsPage = MgmtPage(
    name = "Settings",
    routerPath = "#settings",
    content = _ => <.div("This is the settings page")
  )

  private[this] val helpPage = MgmtPage(
    name = "Help",
    routerPath = "#help",
    content = _ => <.div("This is the help page"),
    loginRequired = false
  )

  private[this] val loginPage : MgmtPage => MgmtPage = p => MgmtPage(
    name = "Login",
    routerPath = loginRouterPath,
    content = n => LoginComponent[MgmtPage](p)(n),
    loginRequired = false
  )

  private[this] val stdLogin = loginPage(ctPage)

  val pages : List[MgmtPage] = List(ctPage, svcPage, profPage, overlaypage, rolloutPage, settingsPage, helpPage)

  // We define a condition to determine whether someone has logged in already
  private[this] val accessGranted : MgmtPage => CallbackTo[Boolean] = p => CallbackTo[Boolean] {
    logger.trace(s"Checking access to page [${p.routeInfo.name}]")
    !p.routeInfo.loginRequired || LoginManager.loggedIn
  }

  // If no user is currently logged, we define an Action to redirect the user to
  // a login page

  private[this] val login : MgmtPage => Action[MgmtPage] = p => RedirectToPath(Path(loginRouterPath), Redirect.Replace)
  private[this] val performLogin : MgmtPage => Option[Action[MgmtPage]] = p => Some(login(p))

  private[this] val routes = RouterConfigDsl[MgmtPage].buildRule { dsl =>
    import dsl._

    val createRule : MgmtPage => Rule = m =>
      staticRoute(m.routeInfo.routerPath, m) ~> renderR( ctl => m.routeInfo.content(NavigationInfo(m, ctl)))

    val loginRule : Rule = createRule(loginPage(ctPage))
    val pageRules : List[Rule] = pages.map(createRule)

    (loginRule :: pageRules).foldLeft(trimSlashes)( (a,b) => a | b )
  }

  private[this] def layout(c: RouterCtl[MgmtPage], r: Resolution[MgmtPage]) : VdomElement  = {

    val isLoginPage = r.page.routeInfo.routerPath.value == loginRouterPath

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

  val routerConfig = RouterConfigDsl[MgmtPage].buildConfig { dsl =>

    import dsl._

    (routes | staticRedirect(root) ~> redirectToPage(ctPage)(Redirect.Replace))
      .addCondition(accessGranted)(performLogin)
      .notFound(redirectToPage(ctPage)(Redirect.Replace))
      .renderWith((ctl, res) => layout(ctl, res))
  }

}
