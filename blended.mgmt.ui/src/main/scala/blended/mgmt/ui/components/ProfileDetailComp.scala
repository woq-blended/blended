package blended.mgmt.ui.components

import blended.mgmt.ui.util.{I18n, Logger}
import blended.updater.config.{BundleConfig, ContainerInfo, RuntimeConfig}
import chandu0101.scalajs.react.components.reacttable.ReactTable
import ReactTable._
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

        val bundles : Seq[Seq[String]] = rc.bundles.map { bc =>
          Seq(bc.url)
        }

        <.div(
          <.h2(
            i18n.tr("Profile:"),
            " ",
            rc.name,
            "-",
            rc.version
          ),
          DataTable.fromProperties(
            panelHeading = "Profile Properties",
            content = rc.properties
          ),
          DataTable.fromProperties(
            panelHeading = "Framework Properties",
            content = rc.frameworkProperties
          ),
          DataTable.fromProperties(
            panelHeading = "System Properties",
            content = rc.systemProperties
          ),
          DataTable.fromProperties(
            panelHeading = "Features",
            content = rc.features.map(f => (f.name, f.version)).toMap,
            headings = ("name", "version")
          ),
          DataTable.fromStringSeq(
            panelHeading = "Bundles",
            content = bundles,
            headings = Seq("url", "autoStart", "startLevel").zipWithIndex
          ),
          DataTable.fromProperties(
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