import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import phoenix.ProjectFactory
import sbt.Keys._
import sbt._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

object BlendedDocsJs extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName :String = "blended.docs"
    override val description : String = "Dummy Js project to download npm modules for the doc generator"
    override val osgi : Boolean = false
    override val projectDir : Option[String] = Some("doc")
    override val publish : Boolean = false

    override def plugins : Seq[AutoPlugin] = super.plugins ++ Seq(
      ScalaJSPlugin,
      ScalaJSBundlerPlugin
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / fork := false,
      Compile / fastOptJS / webpackConfigFile := Some(baseDirectory.value / "docs.webpack.config.js"),
      Compile / fastOptJS / webpackMonitoredDirectories += baseDirectory.value / "scss",
      Compile / fastOptJS / webpackMonitoredFiles / includeFilter := "*.scss",

      Compile / npmDevDependencies ++= Seq(
        "webpack-merge" -> "4.1.2",
        "style-loader" -> "0.23.1",
        "css-loader" -> "1.0.1",
        "sass-loader" -> "^7.1.0",
        "raw-loader" -> "0.5.1",
        "node-sass" -> "4.9.4",
        "extract-text-webpack-plugin" -> "3.0.2"
      ),

      Compile / npmDependencies ++= Seq(
        "perfect-scrollbar" -> "1.4.0",
        "bootstrap" -> "4.1.3",
        "jquery" -> "3.3.1",
        "mermaid" -> "8.0.0-rc.8",
        "mermaid.cli" -> "0.5.1"
      )
    )
  }
}
