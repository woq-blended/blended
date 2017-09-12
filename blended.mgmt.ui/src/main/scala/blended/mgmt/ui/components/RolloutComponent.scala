package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{DataManager, Observer}
import blended.mgmt.ui.util.DisplayHelper
import blended.updater.config._
import japgolly.scalajs.react._
import vdom.html_<^._

object RolloutComponent {

  case class RolloutState(

    container : List[ContainerInfo],

    selectedProfile : Option[ProfileInfo],
    selectedOverlays : Seq[OverlayConfig],
    selectedContainer : Seq[ContainerInfo]
  )

  class Backend(scope : BackendScope[Unit, RolloutState])
    extends Observer[List[ContainerInfo]] {


    override val dataChanged: (List[ContainerInfo]) => Callback = { data =>
      scope.modState(s => s.copy(container = data))
    }

    def render(s: RolloutState) = {

      def containerRow(ctInfo: ContainerInfo) : Seq[String] = {

        val activeProfile = ctInfo.profiles.flatMap(_.toSingle).find(_.state == OverlayState.Active)

        Seq(
          ctInfo.containerId,
          activeProfile.map(DisplayHelper.profileToString).getOrElse(""),
          ctInfo.properties.mkString(", ")
        )
      }

      val container = DataTable.fromStringSeq(
        panelHeading = "Container",
        content = s.container.map(containerRow),
        headings = Seq("Container ID", "Active Profile", "Properties").zipWithIndex,
        selectable = true,
        multiSelectable = true,
        allSelectable = true
      )

      <.div(
        ContentPanel("Profile")(<.div()),
        ContentPanel("Overlays")(<.div()),
        container
      )
    }
  }

  val Component = ScalaComponent.builder[Unit]("Rollout")
    .initialState(RolloutState(
      container = List.empty,
      selectedProfile = None,
      selectedOverlays = Seq.empty,
      selectedContainer = Seq.empty
    ))
    .renderBackend[Backend]
    .componentDidMount(c => DataManager.containerData.addObserver(c.backend))
    .componentWillUnmount(c => DataManager.containerData.removeObserver(c.backend))
    .build

}
