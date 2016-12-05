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
    organization := BlendedVersions.BlendedGroupId,
    version := BlendedVersions.blendedVersion,
    name := appName,
    scalaVersion := BlendedVersions.scalaVersion,
    moduleName := appName,

    // avoid picking up pom.scala as source file
    sourcesInBase := false,

    (unmanagedSourceDirectories in Compile) := Seq(
      baseDirectory.value / "src" / "shared" / "scala",
      baseDirectory.value / "src" / "js" / "scala"
    )
  )

}
