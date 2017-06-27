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

object ProfilesComp {

  private[this] val log: Logger = Logger[ProfilesComp.type]
  private[this] val i18n = I18n()

  case class ContainerProfile(containerId: String, profile: SingleProfile) {
    def name: String = profile.name
    def version: String = profile.version
    def overlays: List[OverlayRef] = profile.overlaySet.overlays
  }

  case class State(containerInfos: List[ContainerInfo], filter: And[Profile] = And(), selected: Option[Profile] = None) {
    def singleProfiles: List[SingleProfile] = containerInfos.flatMap(c => c.profiles.flatMap(p => p.toSingle))
    def containerProfiles: List[ContainerProfile] = containerInfos.flatMap(c => c.profiles.flatMap(p => p.toSingle).map(p => ContainerProfile(c.containerId, p)))
  }

  class Backend(scope: BackendScope[Unit, State]) extends Observer[List[ContainerInfo]] {

    override def update(newData: List[ContainerInfo]): Unit = scope.setState(State(newData)).runNow()
//
//    def selectContainerProfile(profile: Option[]): Callback = {
//      //      scope.modState(s => s.copy(selected = profile).consistent)
//      // FXIME
//      Callback.empty
//    }

    def render(s: State) = {
      log.debug(s"Rerendering with state $s")

      // we want a tree !
      
      val rendered = s.containerProfiles.toSeq.groupBy(cp => cp.name).toSeq.flatMap { case (name, cps) =>
        cps.groupBy(cp => cp.version).toSeq.flatMap { case (version, cps) =>
          cps.groupBy(cp => cp.overlays.toSet).toSeq.map { case (overlaySet, cps) =>
            <.div(name, "-", version, !overlaySet.isEmpty ?= " with ",  <.span(overlaySet.toList.mkString(", ")))
          }
        }
      }
      
      <.div(
        ^.`class` := "row",
        <.div(rendered: _*),
        <.div(
          ProfileDetailComp.Component(ProfileDetailComp.Props(s.selected)))
      )
    }
  }

  val Component =
    ReactComponentB[Unit]("Profiles")
      .initialState(State(containerInfos = List()))
      .renderBackend[Backend]
      .componentDidMount(c => DataManager.containerData.addObserver(c.backend))
      .componentWillUnmount(c => DataManager.containerData.removeObserver(c.backend))
      .build
}