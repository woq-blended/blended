package blended.mgmt.ui.components

import blended.mgmt.ui.backend.DataManager
import blended.mgmt.ui.components.filter.And
import blended.updater.config.ContainerInfo

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.BackendScope
import japgolly.scalajs.react.vdom.prefix_<^._

import blended.mgmt.ui.ConsoleSettings
import blended.mgmt.ui.backend.DirectProfileUpdater
import blended.updater.config.Profile
import blended.mgmt.ui.backend.Observer
import blended.mgmt.ui.components.filter.Filter
import blended.mgmt.ui.util.Logger
import blended.mgmt.ui.util.I18n
import blended.updater.config.Profile.SingleProfile
import blended.updater.config.OverlayRef
import blended.updater.config.OverlayState

object DeploymentProfilesComp {

  private[this] val log: Logger = Logger[DeploymentProfilesComp.type]
  private[this] val i18n = I18n()

  case class DeploymentProfile(containerId: String, profile: SingleProfile) {
    def name: String = profile.name
    def version: String = profile.version
    def overlays: List[OverlayRef] = profile.overlaySet.overlays
  }

  case class State(containerInfos: List[ContainerInfo], filter: And[Profile] = And(), selected: Option[Profile] = None) {
    def singleProfiles: List[SingleProfile] = containerInfos.flatMap(c => c.profiles.flatMap(p => p.toSingle))
    def containerProfiles: List[DeploymentProfile] = containerInfos.flatMap(c => c.profiles.flatMap(p => p.toSingle).map(p => DeploymentProfile(c.containerId, p)))
  }

  class Backend(scope: BackendScope[Unit, State]) extends Observer[List[ContainerInfo]] {

    override def dataChanged(newData: List[ContainerInfo]): Unit = scope.setState(State(newData)).runNow()
    //
    //    def selectContainerProfile(profile: Option[]): Callback = {
    //      //      scope.modState(s => s.copy(selected = profile).consistent)
    //      // FXIME
    //      Callback.empty
    //    }

    def render(s: State) = {
      log.debug(s"Rerendering with state $s")

      // we want a tree !

      val rendered = s.containerProfiles.toSeq.groupBy(cp => cp.name).toSeq.flatMap {
        case (name, cps) =>
          cps.groupBy(cp => cp.version).toSeq.flatMap {
            case (version, cps) =>
              cps.groupBy(cp => cp.overlays.toSet).toSeq.map {
                case (overlaySet, cps) =>
                  val states = cps.map(dp => dp.profile.overlaySet.state)
                  val active = states.filter(OverlayState.Active ==).size
                  val valid = states.filter(OverlayState.Valid ==).size
                  val invalid = states.filter(OverlayState.Invalid ==).size
                  val pending = states.filter(OverlayState.Pending ==).size
                  <.div(
                    name, "-", version,
                    overlaySet.isEmpty ?= " base",
                    !overlaySet.isEmpty ?= " with ",!overlaySet.isEmpty ?= <.span(overlaySet.toList.mkString(", ")),
                    i18n.tr(" ({0} active, {1} valid, {2} pending, {3} invalid)", active, valid, pending, invalid)
                  )
              }
          }
      }

      <.div(
        ^.`class` := "row",
        <.div(rendered: _*),
        <.div(
          DeploymentProfileDetailComp.Component(DeploymentProfileDetailComp.Props(s.selected)))
      )
    }
  }

  val Component =
    ReactComponentB[Unit]("Deployment Profiles")
      .initialState(State(containerInfos = List()))
      .renderBackend[Backend]
      .componentDidMount(c => DataManager.containerData.addObserver(c.backend))
      .componentWillUnmount(c => DataManager.containerData.removeObserver(c.backend))
      .build
}