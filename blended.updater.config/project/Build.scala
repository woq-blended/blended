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
    organization := BlendedVersions.blendedGroupId,
    version := BlendedVersions.blendedVersion,
    name := appName,
    scalaVersion := BlendedVersions.scalaVersion,
    moduleName := appName,

    // avoid picking up pom.scala as source file
    sourcesInBase := false,

    libraryDependencies ++= Dependencies.clientDeps.value,

    (unmanagedSourceDirectories in Compile) := Seq(
      baseDirectory.value / "src" / "shared" / "scala"
    )
  )

  object Dependencies {

    lazy val clientDeps = Def.setting(Seq(
      "com.github.benhutchison" %%% "prickle" % BlendedVersions.prickle
    ))

  }

}
