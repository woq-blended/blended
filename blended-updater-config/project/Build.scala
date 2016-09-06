import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._

object Build extends sbt.Build {

  val appName = "blended.updater.config"

  lazy val root =
    project.in(file("."))
      .settings(projectSettings: _*)
      .enablePlugins(ScalaJSPlugin)

  lazy val projectSettings = Seq(
    organization := "de.wayofquality.blended",
    version := Versions.app,
    name := appName,
    scalaVersion := Versions.scala,

    sourcesInBase := false,

    (unmanagedSourceDirectories in Compile) := Seq(
      baseDirectory.value / "src" / "shared" / "scala",
      baseDirectory.value / "src" / "js" / "scala"
    )

  )

  object Versions {
    val app             = "2.0-SNAPSHOT"
    val scala           = "2.11.8"
  }
}
