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

          def props(ps: Map[String, String]) = ps.map(p => <.div(<.span("  ", p._1, ": "), <.span(p._2))).toSeq

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
              headings = Array("Name", "Version"),
              content = rc.features.map( f => Array(f.name, f.version))
            )),
            DataTableComp.Component(DataTableContent(
              title = "Bundles",
              headings = Array("Url", "AutoStart", "StartLevel"),
              content = rc.bundles.map(b => Array(b.url, s"${b.start}", s"${b.startLevel}"))
            )),
            DataTableComp.Component(DataTableContent(
              title = "Resources",
              headings = Array("Url", "Filename"),
              content = rc.resources.map(r => Array(r.url, r.fileName.getOrElse(""))
            ))
          )
        )
      }
    }
  }

  val Component = ScalaComponent.builder[Props]("RuntimeConfigDetail")
      .renderBackend[Backend]
      .build
}