package blended.mgmt.ui.pages

import blended.mgmt.ui.backend.DataManager
import japgolly.scalajs.react.extra.router.{Path, RouterConfigDsl}
import japgolly.scalajs.react.{Callback, ReactComponentB, ReactElement}

abstract class TopLevelPage {

  val name : String

  val routerPath : Path

  val content : ReactElement

  def component =
    ReactComponentB.static(name, content)
      .componentWillMount(c => Callback {
        Callback.info("foooo " + routerPath)
        DataManager.selectedPage = this
      })
      .build
}

object TopLevelPages {

  val values : List[TopLevelPage] = List(
    ContainerPage,
    ServicesPage,
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
