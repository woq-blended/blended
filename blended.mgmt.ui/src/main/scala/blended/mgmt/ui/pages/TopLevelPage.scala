package blended.mgmt.ui.pages

import blended.mgmt.ui.util.I18n
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.html_<^._

abstract class TopLevelPage {

  /** Use this to internationalize mesages. */
	val i18n = I18n()
  
	/** The name of the page. */
  val name : String

  val routerPath : Path

  val content : VdomElement

  def component = ScalaComponent.builder.static(name)(<.div(content))
    .build
}

object TopLevelPages {

  val values : List[TopLevelPage] = List(
    ContainerPage,
    ServicesPage,
    DeploymentProfilesPage,
    ProfilesPage,
    OverlaysPage,
    RolloutPage,
    SettingsPage,
    HelpPage
  )

  val defaultPage = values.head

}
