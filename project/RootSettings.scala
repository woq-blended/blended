import de.wayofquality.sbt.jbake.JBake.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

object RootSettings {

  def apply(blendedDocs : Project) : Seq[Setting[_]] = Seq(
    publishArtifact := false,
    publish := {},
    publishLocal := {},

    Test / parallelExecution := false,

    jbakeVersion := "2.6.4",
    jbakeMode := System.getenv().getOrDefault("JBAKE_MODE", "build"),
    jbakeInputDir := (blendedDocs / baseDirectory).value,
    jbakeNodeBinDir := {
      (blendedDocs / Compile / fastOptJS / webpack).value
      val modulesDir = (blendedDocs / Compile / fastOptJS / crossTarget).value
      Some(modulesDir / "node_modules" / ".bin")
    },

    jbakeSiteAssets := {
      (blendedDocs / Compile / fastOptJS / webpack).value

      val modulesDir = (blendedDocs / Compile / fastOptJS / crossTarget).value
      val assetDir = (Compile / jbakeOutputDir).value

      Map(
        crossTarget.value / "unidoc" -> assetDir / "scaladoc",
        crossTarget.value / "scoverage-report" -> assetDir / "coverage",
        modulesDir / "blended-bootstrap.css" -> assetDir / "css" / "blended-bootstrap.css",
        modulesDir / "node_modules" / "bootstrap" / "dist" / "js" / "bootstrap.min.js" -> assetDir / "js" / "bootstrap.min.js",
        modulesDir / "node_modules" / "jquery" / "dist" / "jquery.min.js" -> assetDir / "js" / "jquery.min.js",
        modulesDir / "node_modules" / "perfect-scrollbar" / "dist" / "perfect-scrollbar.js" -> assetDir / "js" / "perfect-scrollbar.js",
        modulesDir / "node_modules" / "perfect-scrollbar" / "css" / "perfect-scrollbar.css" -> assetDir / "css" / "perfect-scrollbar.css"
      )
    }
  )
}
