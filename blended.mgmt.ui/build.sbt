import sbt.Keys._
import sbt._

val appName = "blended.mgmt.ui"
val jsDir = "target"

lazy val root = project
  .in(file("."))
  .settings(
    organization := BlendedVersions.blendedGroupId,
    version := BlendedVersions.blendedVersion,
    name := appName,

    scalaJSUseMainModuleInitializer := true,
    scalaJSUseMainModuleInitializer in Test := false,

    crossTarget in (Compile, fullOptJS) := file(jsDir),
    crossTarget in (Compile, fastOptJS) := file(jsDir),

    artifactPath in (Compile, fastOptJS) :=
      ((crossTarget in (Compile, fastOptJS)).value / ((moduleName in fastOptJS).value + "-opt.js")),

    scalaVersion := BlendedVersions.scalaVersion,
    sourcesInBase := false,

    resolvers += Resolver.mavenLocal,

    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % Versions.scalajsReact,
      "com.github.japgolly.scalajs-react" %%% "extra" % Versions.scalajsReact,
      "com.github.japgolly.scalacss" %%% "ext-react" % Versions.scalaCss,

      "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom,
      organization.value %%% "blended.updater.config" % BlendedVersions.blendedVersion,
      "com.github.benhutchison" %%% "prickle" % BlendedVersions.prickle,
      "com.olvind" %%% "scalajs-react-components" % "0.8.1",

      "com.github.japgolly.scalajs-react" %%% "test" % Versions.scalajsReact % "test",
      "org.scalatest" %%% "scalatest" % BlendedVersions.scalaTestVersion % "test"
    ),

    emitSourceMaps := true

  )
  .enablePlugins(ScalaJSPlugin)