package blended.mgmt.ui.components

import blended.updater.config.ContainerInfo
import japgolly.scalajs.react.ReactComponentB
import japgolly.scalajs.react.ReactEventI
import japgolly.scalajs.react.vdom.prefix_<^._
import blended.mgmt.ui.util.I18n
import blended.mgmt.ui.util.Logger
import japgolly.scalajs.react.BackendScope
import blended.updater.config.OverlayState
import blended.updater.config.Profile
import blended.updater.config.OverlaySet
import japgolly.scalajs.react.CallbackTo
import blended.mgmt.ui.backend.ProfileUpdater
import blended.updater.config.ActivateProfile
import blended.updater.config.UpdateAction
import blended.updater.config.StageProfile

object CompContainerDetail {

  private[this] val log = Logger[CompContainerDetail.type]
  private[this] val i18n = I18n()

  case class Props(containerInfo: Option[ContainerInfo] = None, profileUpdater: Option[ProfileUpdater] = None)

  class Backend(scope: BackendScope[Props, Unit]) {

    def sendUpdateAction(updateActions: UpdateAction*)(event: ReactEventI) = {
      scope.props.map { p =>
        p.containerInfo match {
          case Some(ci) =>
            p.profileUpdater match {
              case Some(pu) =>
                pu.addUpdateActions(ci.containerId, updateActions.toList)
              case None =>
                log.info(s"Skipping update action. No profile updater set")
            }
          case None =>
            log.info(s"Skipping update action. No containerInfo set")
        }
      }
    }

    def activateProfile(profile: Profile, overlaySet: OverlaySet)(event: ReactEventI) = {
      sendUpdateAction(ActivateProfile(profile.name, profile.version, overlaySet.overlays))(event)
    }

    def resolveProfile(profile: Profile, overlaySet: OverlaySet)(event: ReactEventI) = {
      sendUpdateAction(StageProfile(profile.name, profile.version, overlaySet.overlays))(event)
    }

    def deleteProfile(profile: Profile, overlaySet: OverlaySet)(event: ReactEventI) = {
      CallbackTo {
        log.trace(s"Unimplemented callback: delete profile ${profile} with overlay set ${overlaySet}")
      }
    }

    def render(props: Props) = {
      props match {
        case Props(None, _) => <.span(i18n.tr("No Container selected"))
        case Props(Some(containerInfo), profileUpdater) =>

          val props = containerInfo.properties.map(p => <.div(<.span("  ", p._1, ": "), <.span(p._2))).toSeq

          val profiles = containerInfo.profiles.flatMap { profile =>
            val oSets = profile.overlays

            oSets.map { oSet =>
              val overlays = oSet.overlays

              val genTitle = if (overlays.isEmpty) i18n.tr("without overlays") else overlays.mkString(", ")

              <.div(
                ^.`class` := oSet.state.toString,
                s"${profile.name}-${profile.version} ${genTitle} ",
                <.span(
                  oSet.reason.isDefined ?= (^.title := s"${oSet.state}: ${oSet.reason.get}"),
                  s"(${oSet.state})"
                ),
                " ",
                oSet.state != OverlayState.Active ?= <.button(
                  ^.`type` := "button",
                  ^.`class` := "btn btn-default btn-xs",
                  profileUpdater.isEmpty ?= (^.disabled := "disabled"),
                  ^.onClick ==> activateProfile(profile, oSet),
                  i18n.tr("Activate")
                ),
                " ",
                oSet.state != OverlayState.Active ?= <.button(
                  ^.`type` := "button",
                  ^.`class` := "btn btn-default btn-xs",
                  profileUpdater.isEmpty ?= (^.disabled := "disabled"),
                  ^.onClick ==> deleteProfile(profile, oSet),
                  i18n.tr("Delete")
                ),
                " ",
                oSet.state == OverlayState.Invalid ?= <.button(
                  ^.`type` := "button",
                  ^.`class` := "btn btn-default btn-xs",
                  profileUpdater.isEmpty ?= (^.disabled := "disabled"),
                  ^.onClick ==> resolveProfile(profile, oSet),
                  i18n.tr("Try to Resolve")
                )
              )
            }
          }

          val services = containerInfo.serviceInfos.map { serviceInfo =>

            val sProps = serviceInfo.props.map(p => <.div(<.span("  ", p._1, ": "), <.span(p._2))).toSeq

            <.div(
              <.div(
                i18n.tr("Service: "),
                serviceInfo.name
              ),
              <.div(
                i18n.tr("Type: "),
                serviceInfo.serviceType
              ),
              <.div(
                i18n.tr("Properties: "),
                <.div(sProps: _*)
              )
            )
          }

          <.div(
            <.div(
              i18n.tr("Container ID:"),
              " ",
              containerInfo.containerId
            ),
            <.div(
              i18n.tr("Properties:"),
              <.div(props: _*)
            ),
            <.div(
              i18n.tr("Profiles:"),
              <.div(profiles: _*)
            ),
            <.div(
              i18n.tr("Services:"),
              <.div(services: _*)
            )
          )

      }
    }
  }

  val Component =
    ReactComponentB[Props]("ContainerDetail")
      .renderBackend[Backend]
      .build
}