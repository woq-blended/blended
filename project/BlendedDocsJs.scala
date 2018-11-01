import org.scalajs.sbtplugin.ScalaJSPlugin
import sbt._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

object BlendedDocsJs extends ProjectFactory {

  val helper = new ProjectSettings(
    projectName = "blended.docs",
    description = "Dummy Js project to download npm modules for the doc generator",
    osgi = false
  ) {
    override val projectDir: Option[String] = Some("doc")
    override def plugins: Seq[AutoPlugin] = Seq(ScalaJSPlugin, ScalaJSBundlerPlugin)

    override def settings: Seq[sbt.Setting[_]] = Seq(
      Compile / npmDependencies ++= Seq(
        "mermaid" -> "^8.0.0-rc.8",
        "mermaid.cli" -> "^0.5.1"
      )
    )
  }

  override val project = helper.baseProject
}
