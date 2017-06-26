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
import blended.updater.config.OverlayConfig

/**
 * React component to render details of a [[ContainerInfo]].
 */
object OverlayConfigDetailComp {

  private[this] val log = Logger[OverlayConfigDetailComp.type]
  private[this] val i18n = I18n()

  case class Props(runtimeConfig: Option[OverlayConfig] = None)

  class Backend(scope: BackendScope[Props, Unit]) {

    def render(props: Props) = {
      props match {
        case Props(None) => <.span(i18n.tr("No OverlayConfig selected"))
        case Props(Some(oc)) =>

          def props(ps: Map[String, String]) = ps.map(p => <.div(<.span("  ", p._1, ": "), <.span(p._2))).toSeq

          val genConf = oc.generatedConfigs.map(c => <.div(c.configFile, <.pre(c.config)))

          <.div(
            <.h2(
              i18n.tr("Overlay Config:"),
              " ",
              oc.name,
              "-",
              oc.version
            ),
            <.div(
              i18n.tr("Properties:"),
              <.div(props(oc.properties): _*)
            ),
            <.div(
              i18n.tr("Generated Configs:"),
              <.div(
                genConf: _*
              )
            )
          )
      }
    }
  }

  val Component =
    ReactComponentB[Props]("OverlayConfigDetail")
      .renderBackend[Backend]
      .build
}