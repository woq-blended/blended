package blended.mgmt.ui.pages

import blended.mgmt.ui.util.I18n
import japgolly.scalajs.react.extra.router.{Path, RouterConfigDsl}
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.VdomElement

abstract class TopLevelPage {

	val i18n = I18n()
  
  val name : String

  val routerPath : Path

  val content : VdomElement

  def component = ScalaComponent.builder.static(name)(content)
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

}
