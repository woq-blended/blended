import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import com.typesafe.sbt.web.SbtWeb.autoImport._
import com.typesafe.sbt.less.Import._
import com.typesafe.sbt.web.SbtWeb
import sbt.Keys._
import sbt._

object MgmtUiBuild extends Build {

  val appName = "blended.mgmt.ui"

  lazy val root =
    project.in(file("."))
      .settings(projectSettings: _*)
      .enablePlugins(ScalaJSPlugin, SbtWeb)

  lazy val projectSettings = Seq(
    organization := "de.wayofquality.blended",
    version := Versions.app,
    name := appName,
    scalaVersion := Versions.scala,

    mainClass in Compile := Some("tutorial.webapp.TutorialApp"),

    LessKeys.sourceMap in Assets := true,      // generate a source map for developing in the browser
    LessKeys.compress in Assets := true,       // Compress the final CSS
    LessKeys.color in Assets := true,          // Colorise Less output
    LessKeys.sourceMapLessInline := false,     // Have less files extra

    (sourceDirectory in Assets) := (baseDirectory.value / "src" / "main" / "less"),
    includeFilter in (Assets, LessKeys.less) := "main.less",
    (compile in Compile) <<= (compile in Compile) dependsOn (LessKeys.less in Compile),

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
      "be.doeraene" %%% "scalajs-jquery" % Versions.scalajsJQuery,
      "org.webjars" % "bootstrap" % Versions.bootstrap,
      "org.webjars" % "react" % Versions.react
    ))
  }
}

