package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{DataManager, Observer}
import blended.mgmt.ui.util.DisplayHelper
import blended.updater.config._
import chandu0101.scalajs.react.components.reacttable.ReactTable
import japgolly.scalajs.react._
import vdom.html_<^._

object RolloutComponent {

  case class RolloutState(

    container : List[ContainerInfo],
    overlays : List[OverlayConfig],
    profiles : List[RuntimeConfig],

    selectedProfile : Option[RuntimeConfig],
    selectedOverlays : Seq[OverlayConfig],
    selectedContainer : Seq[ContainerInfo]
  )

  class Backend(scope : BackendScope[Unit, RolloutState]) {

    val ctObserver = new Observer[List[ContainerInfo]] {
      override val dataChanged = { data : List[ContainerInfo] =>
        scope.modState(_.copy(container = data))
      }
    }

    val overlayObserver = new Observer[List[OverlayConfig]] {
      override val dataChanged = { data : List[OverlayConfig] =>
        scope.modState(_.copy(overlays = data))
      }
    }

    val rtObserver = new Observer[List[RuntimeConfig]] {
      override val dataChanged = { data : List[RuntimeConfig] =>
        scope.modState(_.copy(profiles = data))
      }
    }

    val selectContainer : Set[(ContainerInfo, String)] => Callback = { selected =>
      scope.modState(s => s.copy(selectedContainer = selected.map(_._1).toSeq))
    }

    val deploy : RolloutState => Callback = s => Callback {
      println("Deploying")
    }

    val selectProfile : Set[(RuntimeConfig, String)] => Callback = { selected =>
      scope.modState(s =>
        s.copy(
          selectedProfile = selected match {
            case e if e.isEmpty => None
            case s  => Some(s.head._1)
          }
        )
      )
    }

    val selectOverlays : Set[(OverlayConfig, String)] => Callback = { selected =>
      scope.modState( _.copy(selectedOverlays = selected.map(_._1).toSeq ))
    }

    def render(s: RolloutState) = {

      val profileKey : RuntimeConfig => String = p => p.name + "-" + p.version

      val profiles = ContentPanel("Profile")(
        ReactTable[RuntimeConfig](
          data = s.profiles,
          configs = Seq(
            ReactTable.SimpleStringConfig("Name", _.name),
            ReactTable.SimpleStringConfig("Version", _.version)
          ),
          paging = false,
          selectable = true,
          multiSelectable = false,
          searchStringRetriever = (cfg => cfg.name + cfg.version),
          keyStringRetriever = profileKey,
          onSelectionChanged = selectProfile,
          initialSelection = s.selectedProfile.map(profileKey).toSeq
        )()
      )

      val overlayKey : OverlayConfig => String = cfg => cfg.name + "-" + cfg.version

      val overlayTable = ContentPanel("Overlays")(
        ReactTable[OverlayConfig](
          data = s.overlays,
          configs = Seq(
            ReactTable.SimpleStringConfig("Name", _.name),
            ReactTable.SimpleStringConfig("Version", _.version)
          ),
          paging = false,
          selectable = true,
          multiSelectable = true,
          allSelectable = true,
          searchStringRetriever = (cfg => cfg.name + cfg.version),
          keyStringRetriever = overlayKey,
          onSelectionChanged = selectOverlays,
          initialSelection = s.selectedOverlays.map(overlayKey)
        )()
      )

      val container = {
        val profileString : ContainerInfo => String = { ctInfo : ContainerInfo =>
          val activeProfile = ctInfo.profiles.flatMap(_.toSingle).find(_.state == OverlayState.Active)
          activeProfile.map(DisplayHelper.profileToString).getOrElse("")
        }

        ContentPanel("Container")(
          ReactTable[ContainerInfo](
            data = s.container,
            configs = Seq(
              ReactTable.SimpleStringConfig("Container ID", _.containerId),
              ReactTable.SimpleStringConfig("Active Profile", profileString),
              ReactTable.SimpleStringConfig("Properties", _.properties.mkString(", "))
            ),
            paging = false,
            selectable = true,
            multiSelectable = true,
            allSelectable = true,
            searchStringRetriever = (ct => ct.containerId + profileString(ct) + ct.properties.mkString),
            keyStringRetriever = _.containerId,
            initialSelection = s.selectedContainer.map(_.containerId),
            onSelectionChanged = selectContainer
          )()
        )
      }

      val deployable =
        s.selectedProfile.isDefined &&
        s.selectedContainer.size > 0

      val rollout = if (deployable) {
        ContentPanel("Deploy")(
          <.div(
            ^.display := "flex",
            ^.flexDirection := "column",
            <.div(
              ^.display := "flex",
              ^.flexDirection := "row",
              <.div(^.flex := "1", "Profile to be deployed : "),
              <.div(^.flex := "1", s"${s.selectedProfile.map(rc => rc.name + "-" + rc.version).getOrElse("")}")
            ),
            <.div(
              ^.display := "flex",
              ^.flexDirection := "row",
              <.div(^.flex := "1", "Overlays to be deployed : "),
              <.div(^.flex := "1", s.selectedOverlays.map(oc => oc.name + "-" + oc.version).mkString(","))
            ).unless(s.selectedOverlays.isEmpty),
            <.div(
              ^.height := "2rem"
            ),
            <.div(
              ^.margin := "auto",
              <.button(
                ^.cls := "btn btn-primary btn-lg",
                ^.onClick --> deploy(s),
                s"Deploy to ${s.selectedContainer.size} containers"
              )
            )
          )
        )
      } else {
        ContentPanel("Deploy")(
          "You have to select one Profile and least one Container for deployment."
        )
      }

      <.div(
        profiles,
        overlayTable,
        container,
        rollout
      )
    }
  }

  val Component = ScalaComponent.builder[Unit]("Rollout")
    .initialState(RolloutState(
      container = List.empty,
      overlays = List.empty,
      profiles = List.empty,
      selectedProfile = None,
      selectedOverlays = Seq.empty,
      selectedContainer = Seq.empty
    ))
    .renderBackend[Backend]
    .componentDidMount{ c => Callback {
      DataManager.containerData.addObserver(c.backend.ctObserver)
      DataManager.overlayConfigsData.addObserver(c.backend.overlayObserver)
      DataManager.runtimeConfigsData.addObserver(c.backend.rtObserver)
    }}
    .componentWillUnmount{ c => Callback {
      DataManager.containerData.removeObserver(c.backend.ctObserver)
      DataManager.overlayConfigsData.removeObserver(c.backend.overlayObserver)
      DataManager.runtimeConfigsData.removeObserver(c.backend.rtObserver)
    }}
    .build

}
