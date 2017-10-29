import sbt.Keys._
import sbt._
import com.typesafe.sbt.osgi.SbtOsgi.autoImport._

val m2Repo = "file://" + System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository")

lazy val defaultSettings : Seq[Def.SettingsDefinition] = Seq(
  organization := BlendedVersions.blendedGroupId,
  version := BlendedVersions.blended,

  scalaVersion := BlendedVersions.scala,
  scalacOptions ++= Seq("-deprecation", "-feature", "-Xlint", "-Ywarn-nullary-override"),
  sourcesInBase := false
)

lazy val root = project
  .in(file("."))
  .settings(defaultSettings:_*)
  .settings(
    name := "blended"
  )
  .enablePlugins(ScalaUnidocPlugin)
  .aggregate(blendedUtil)

lazy val blendedUtil = project
  .in(file("blended.util"))
  .settings(defaultSettings:_*)
  .settings(
    name := "blended.util",
    description := "Utility classes to use in other bundles.",

    osgiSettings,

    libraryDependencies ++= Seq(
      Dependencies.akkaActor,
      Dependencies.slf4j,
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.scalatest % "test",
      Dependencies.junit % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )
  )
  .settings(BuildHelper.bundleSettings(
    symbolicName = "blended.util",
    exports = Seq("", "protocol")):_*
  )
  .enablePlugins(SbtOsgi)
