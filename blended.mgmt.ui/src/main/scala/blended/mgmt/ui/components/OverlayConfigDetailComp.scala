package blended.mgmt.ui.components

import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.{ContainerInfo, OverlayConfig}
import japgolly.scalajs.react._
import vdom.html_<^._

import scalajs.js.JSON

/**
 * React component to render details of a [[ContainerInfo]].
 */
object OverlayConfigDetailComp {

  private[this] val log = Logger[OverlayConfigDetailComp.type]
  private[this] val i18n = I18n()

  case class Props(runtimeConfig: Option[OverlayConfig] = None)

  class Backend(scope: BackendScope[Props, Unit]) {

    def render(props: Props) : VdomElement = {
      props match {
        case Props(None) => <.span(i18n.tr("No OverlayConfig selected"))
        case Props(Some(oc)) =>

          val replacer: scalajs.js.Array[scalajs.js.Any] = null

          val genConf: TagMod = TagMod(oc.generatedConfigs.map { c =>

            val obj: scalajs.js.Any = JSON.parse(c.config)
            val formatted = JSON.stringify(obj, replacer, 2)

            <.div(
              ^.cls := "panel panel-default",
              <.div(
                ^.cls := "panel-heading",
                <.h3(c.configFile)
              ),
              <.pre(formatted)
            )
          }: _*)

          ContentPanel("Overlay Config")(TagMod(
            <.h3("oc.name + \" \" + i18n.tr(\"Version\") + \" \" + oc.version"),
            DataTable.fromProperties(
              panelHeading = "Properties",
              content = oc.properties
            ),
            ContentPanel("Generated Configs")(genConf)
          ))
      }
    }
  }

  val Component = ScalaComponent.builder[Props]("OverlayConfigDetail")
    .renderBackend[Backend]
    .build
}