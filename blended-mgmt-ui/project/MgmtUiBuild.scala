import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._

object MgmtUiBuild extends Build {

  val appName = "blended.mgmt.ui"

  lazy val root =
    project.in(file("."))
      .settings(projectSettings: _*)
      .enablePlugins(ScalaJSPlugin)

  lazy val projectSettings = Seq(
    organization := "de.wayofquality.blended",
    version := Versions.app,
    name := appName,
    scalaVersion := Versions.scala,
    mainClass in Compile := Some("tutorial.webapp.TutorialApp"),
    libraryDependencies ++= Dependencies.clientDeps.value,
    persistLauncher in Compile := true,
    persistLauncher in Test := false
  )


  object Dependencies {

    lazy val clientDeps = Def.setting(Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % Versions.scalajsReact,
      "com.github.japgolly.scalajs-react" %%% "test" % Versions.scalajsReact % "test",
      "com.lihaoyi" %%% "upickle" % Versions.upickle,
      "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom,
      "com.lihaoyi" %%% "scalatags" % Versions.scalaTags,
      "be.doeraene" %%% "scalajs-jquery" % Versions.scalajsJQuery
    ))
  }
}

