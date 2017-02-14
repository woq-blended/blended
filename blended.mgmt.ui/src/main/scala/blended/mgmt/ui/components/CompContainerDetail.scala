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

object CompContainerDetail {

  private[this] val log = Logger[CompContainerDetail.type]
  private[this] val i18n = I18n()

  case class Props(containerInfo: Option[ContainerInfo])

  class Backend(scope: BackendScope[Props, Unit]) {

    def activateProfile(profile: Profile, overlaySet: OverlaySet)(event: ReactEventI) = {
      CallbackTo {
        log.trace(s"Unimplemented callback: activate profile ${profile} with overlay set ${overlaySet}")
      }
    }

    def resolveProfile(profile: Profile, overlaySet: OverlaySet)(event: ReactEventI) = {
      CallbackTo {
        log.trace(s"Unimplemented callback: resolve profile ${profile} with overlay set ${overlaySet}")
      }
    }

    def deleteProfile(profile: Profile, overlaySet: OverlaySet)(event: ReactEventI) = {
      CallbackTo {
        log.trace(s"Unimplemented callback: delete profile ${profile} with overlay set ${overlaySet}")
      }
    }

    def render(props: Props) = {
      props match {
        case Props(None) => <.span(i18n.tr("No Container selected"))
        case Props(Some(containerInfo)) =>

          val props = containerInfo.properties.map(p => <.div(<.span(p._1, ": "), <.span(p._2))).toSeq

          val profiles = containerInfo.profiles.flatMap { profile =>
            val oSets = profile.overlays

            oSets.map { oSet =>
              val overlays = oSet.overlays

              val genTitle = if (overlays.isEmpty) i18n.tr("without overlays") else overlays.mkString(", ")

              <.div(
                ^.`class` := oSet.state.toString,
                oSet.reason.isDefined ?= (^.title := s"${oSet.state}: ${oSet.reason.get}"),
                s"${profile.name}-${profile.version} ${genTitle} (${oSet.state})",
                " ",
                oSet.state != OverlayState.Valid ?= <.button(
                  ^.`type` := "button",
                  ^.`class` := "btn btn-default btn-xs",
                  ^.onClick ==> activateProfile(profile, oSet),
                  i18n.tr("Activate")
                ),
                " ",
                oSet.state != OverlayState.Active ?= <.button(
                  ^.`type` := "button",
                  ^.`class` := "btn btn-default btn-xs",
                  ^.onClick ==> deleteProfile(profile, oSet),
                  i18n.tr("Delete")
                ),
                " ",
                oSet.state != OverlayState.Invalid ?= <.button(
                  ^.`type` := "button",
                  ^.`class` := "btn btn-default btn-xs",
                  ^.onClick ==> resolveProfile(profile, oSet),
                  i18n.tr("Try to Resolve")
                )
              )
            }
          }

          <.div(
            <.div(
              i18n.tr("Container ID: "),
              containerInfo.containerId
            ),
            <.div(
              i18n.tr("Properties: "),
              " ",
              <.div(props: _*)
            ),
            <.div(
              i18n.tr("Profiles: "),
              <.div(profiles: _*)
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