package blended.mgmt.ui.components

import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.{ContainerInfo, RuntimeConfig}
import japgolly.scalajs.react._
import vdom.html_<^._

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
        case Props(None) => <.span(i18n.tr("No Profiles selected"))
        case Props(Some(rc)) =>

        <.div(
          <.h2(
            i18n.tr("Profile:"),
            " ",
            rc.name,
            "-",
            rc.version
          ),
          DataTableComp.Component(DataTableContent(
            title = "Profile Properties",
            content = rc.properties
          )),
          DataTableComp.Component(DataTableContent(
            title = "Framework Properties",
            content = rc.frameworkProperties
          )),
          DataTableComp.Component(DataTableContent(
            title = "System Properties",
            content = rc.systemProperties
          )),
          DataTableComp.Component(DataTableContent(
            title = "Features",
            headings = List("name", "version"),
            content = rc.features.map( f => Map("name" -> f.name, "version" -> f.version)).toVector
          )),
          DataTableComp.Component(DataTableContent(
            title = "Bundles",
            headings = List("url", "autoStart", "startLevel"),
            content = rc.bundles.map(b => Map("url" -> b.url, "autoStart" -> b.start, "startLevel" -> b.startLevel)).toVector
          )),
          DataTableComp.Component(DataTableContent(
            title = "Resources",
            headings = List("url", "filename"),
            content = rc.resources.map(r => Map("url" -> r.url, "filename" -> r.fileName.getOrElse(""))).toVector
          ))
        )
      }
    }
  }

  val Component = ScalaComponent.builder[Props]("RuntimeConfigDetail")
      .renderBackend[Backend]
      .build
}