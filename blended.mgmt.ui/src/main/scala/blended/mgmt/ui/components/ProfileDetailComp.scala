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
import blended.updater.config.Profile.SingleProfile
import blended.updater.config.RuntimeConfig

/**
 * React component to render details of a [[ContainerInfo]].
 */
object ProfileDetailComp {

  private[this] val log = Logger[ProfileDetailComp.type]
  private[this] val i18n = I18n()

  case class Props(runtimeConfig: Option[RuntimeConfig] = None)

  class Backend(scope: BackendScope[Props, Unit]) {

    def render(props: Props) = {
      props match {
        case Props(None) => <.span(i18n.tr("No RuntimeConfig selected"))
        case Props(Some(rc)) =>

          def props(ps: Map[String, String]) = ps.map(p => <.div(<.span("  ", p._1, ": "), <.span(p._2))).toSeq

          <.div(
            <.h2(
              i18n.tr("Runtime Config:"),
              " ",
              rc.name,
              "-",
              rc.version
            ),
            <.div(
              i18n.tr("Properties:"),
              <.div(props(rc.properties): _*)
            ),
            <.div(
              i18n.tr("Framework Properties:"),
              <.div(props(rc.frameworkProperties): _*)
            ),
            <.div(
              i18n.tr("System Properties:"),
              <.div(props(rc.systemProperties): _*)
            ),
            <.div(
              i18n.tr("Features:"),
              <.div(rc.features.map(f => <.span(f.name, "-", f.version)): _*)
            ),
            <.div(
              i18n.tr("Bundles:"),
              <.div(rc.bundles.map(b => <.span(b.url)): _*)
            ),
            <.div(
              i18n.tr("Resources:"),
              <.div(rc.resources.map(b => <.span(b.url)): _*)
            ),
            <.span()
          )
      }
    }
  }

  val Component =
    ReactComponentB[Props]("RuntimeConfigDetail")
      .renderBackend[Backend]
      .build
}