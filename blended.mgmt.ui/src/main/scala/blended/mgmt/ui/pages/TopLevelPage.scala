package blended.mgmt.ui.pages

import blended.mgmt.ui.util.I18n
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.Path
import japgolly.scalajs.react.vdom.html_<^._

abstract class TopLevelPage {

	val i18n = I18n()
  
  val name : String

  val routerPath : Path

  // If set to true the first Component will be on the left hand side
  // If set to false the first Component will be on the top
  val verticalFirst : Boolean = true

  val firstComponent : Option[VdomElement] = None

  val secondComponent : Option[VdomElement] = None

  val mainContent : VdomElement

  def component = ScalaComponent.builder.static(name)(<.div(mainContent))
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
