package blended.mgmt.ui.components

import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.{BundleConfig, ContainerInfo, RuntimeConfig}
import chandu0101.scalajs.react.components.ReactTable
import ReactTable._
import chandu0101.scalajs.react.components.ReactTable.ColumnConfig
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
          PropertyTable("Profile Properties", rc.properties),
          PropertyTable("Framework Properties", rc.frameworkProperties),
          PropertyTable("System Properties", rc.systemProperties),
          PropertyTable(
            "Features",
            rc.features.map(f => (f.name, f.version)).toMap,
            ("name", "version")
          ),
          ReactTable(
            data = rc.bundles,
            configs = List(
              ColumnConfig[BundleConfig](name = i18n.tr("url"),  bc => <.span(bc.url))(ignoreCaseStringOrdering(_.url)),
              ColumnConfig[BundleConfig](name = i18n.tr("autoStart"),  bc => <.span(bc.start.toString()))(ignoreCaseStringOrdering(_.start.toString())),
              ColumnConfig[BundleConfig](name = i18n.tr("startLevel"),  bc => <.span(bc.startLevel.toString()))(DefaultOrdering(_.startLevel))
            )
          )(),
          PropertyTable(
            "Resources",
            rc.resources.map(r => (r.url, r.fileName.getOrElse(""))).toMap,
            ("url", "filename")
          )
        )
      }
    }
  }

  val Component = ScalaComponent.builder[Props]("RuntimeConfigDetail")
      .renderBackend[Backend]
      .build
}