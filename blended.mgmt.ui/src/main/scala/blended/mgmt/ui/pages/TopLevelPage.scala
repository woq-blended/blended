package blended.mgmt.ui.pages

import blended.mgmt.ui.util.I18n
import japgolly.scalajs.react.extra.router.{Path, RouterConfigDsl}
import japgolly.scalajs.react.{ReactComponentB, ReactElement}

abstract class TopLevelPage {

	val i18n = I18n()
  
  val name : String

  val routerPath : Path

  val content : ReactElement

  def component =
    ReactComponentB.static(name, content)
      .build
}

object TopLevelPages {

  val values : List[TopLevelPage] = List(
    ContainerPage,
    ServicesPage,
    DeploymentProfilesPage,
    ProfilesPage,
    OverlaysPage,
    SettingsPage,
    HelpPage
  )

  val defaultPage = values.head

  def routes = RouterConfigDsl[TopLevelPage].buildRule { dsl =>
    import dsl._

    values.foldLeft(trimSlashes){ (rule, page) =>
      rule | staticRoute(page.routerPath, page) ~> renderR(_ => page.component())
    }
  }

}
