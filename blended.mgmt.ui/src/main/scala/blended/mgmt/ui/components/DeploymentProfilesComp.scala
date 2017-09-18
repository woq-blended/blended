package blended.mgmt.ui.components

import blended.mgmt.ui.backend.{DataManager, Observer}
import blended.mgmt.ui.components.filter.And
import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config._
import chandu0101.scalajs.react.components.reacttable.ReactTable
import chandu0101.scalajs.react.components.reacttable.ReactTable.CellRenderer
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

object DeploymentProfilesComp {

  case class DeploymentProfile(
    containerId: String,
    profile: SingleProfile
  ) {
    def name: String = profile.name
    def version: String = profile.version
    def overlays: List[OverlayRef] = profile.overlaySet.overlays
  }

  type RowType = (String, String, String, List[DeploymentProfile])

  private[this] val log: Logger = Logger[DeploymentProfilesComp.type]
  private[this] val i18n = I18n()

  case class State(
    containerInfos: List[ContainerInfo],
    filter: And[Profile] = And(),
    selected: Option[RowType] = None
  ) {
    def containerProfiles: List[DeploymentProfile] = containerInfos.flatMap(c =>
      c.profiles.flatMap(_.toSingle).map(p => DeploymentProfile(c.containerId, p))
    )
  }

  class Backend(scope: BackendScope[Unit, State]) extends Observer[List[ContainerInfo]] {

    override val dataChanged = { newData: List[ContainerInfo] =>
      scope.modState(_.copy(containerInfos = newData))
    }

    val selectProfile : Set[(RowType, String)] => Callback = { p : Set[(RowType, String)]=>

      scope.modState{ s =>

        val newSelected : Option[RowType] = if (p.isEmpty)
          None
        else
          Some(p.head._1)

        s.copy(selected = newSelected)
      }
    }

    val overlayKey : SingleProfile => String = sp => sp.overlaySet.overlays match {
      case Nil => "None"
      case l => l.map(or => s"${or.name}-${or.version}").mkString(",")
    }

    val profileKey : RowType => String = p => p._1 + p._2 + p._3

    def render(s: State) = {
      // we want a tree !

      val stateRenderer : CellRenderer[RowType] = { row =>

        val stateDisplay : List[DeploymentProfile] => VdomElement = {
          row : List[DeploymentProfile] =>

            val states = row.map(dp => dp.profile.overlaySet.state)

            val active = states.count(OverlayState.Active ==)
            val valid = states.count(OverlayState.Valid ==)
            val invalid = states.count(OverlayState.Invalid ==)
            val pending = states.count(OverlayState.Pending ==)

            <.div(i18n.tr(" ({0} active, {1} valid, {2} pending, {3} invalid)", active, valid, pending, invalid))
        }

        stateDisplay(row._4)
      }


      val profByName = s.containerProfiles.groupBy(cp => cp.name).toSeq

      val profByVersion = profByName.flatMap{ case (name, dps) =>
        dps.groupBy(_.version).map{ case (version,dp) => (name, version, dp) }
      }

      val profByOverlays = profByVersion.flatMap{ case (name, version, dps) =>
        dps.groupBy(_.overlays).map{ case (o, dp) => (name, version, overlayKey(dp.head.profile), dps) }
      }

      val selectedSingle : Option[SingleProfile] = s.selected.map(
        dp => dp._4.head.profile
      )

      val selectedKeys = s.selected.map(profileKey)
      println(selectedKeys)

      <.div(
        ReactTable[RowType](
          data = profByOverlays,
          configs = Seq(
            ReactTable.SimpleStringConfig("Profile Name", _._1),
            ReactTable.SimpleStringConfig("Profile Version", _._2),
            ReactTable.SimpleStringConfig("Overlays", _._3),
            ReactTable.ColumnConfig("States", stateRenderer)
          ),
          paging = true,
          enableSettings = false,
          rowsPerPage = 10,
          selectable = true,
          multiSelectable = false,
          allSelectable = false,
          keyStringRetriever = profileKey,
          searchStringRetriever = profileKey,
          onSelectionChanged = selectProfile,
          initialSelection = s.selected.map(profileKey).toSeq
        )(),

        DeploymentProfileDetailComp(selectedSingle)()
      )

    }
  }

  val Component = ScalaComponent.builder[Unit]("Deployment Profiles")
    .initialState(State(containerInfos = List()))
    .renderBackend[Backend]
    .componentDidMount(c => Callback { DataManager.containerData.addObserver(c.backend)})
    .componentWillUnmount(c => Callback { DataManager.containerData.removeObserver(c.backend)})
    .build
}