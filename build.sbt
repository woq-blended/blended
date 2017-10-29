import sbt.Keys._
import sbt._

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
  .aggregate(
    blendedUtil,
    blendedTestsupport
  )

lazy val blendedUtil = BuildHelper.blendedOsgiProject(
  pName = "blended.util",
  pDescription = Some("Utility classes to use in other bundles."),
  deps = Seq(
    Dependencies.akkaActor,
    Dependencies.slf4j,
    Dependencies.akkaTestkit % "test",
    Dependencies.akkaSlf4j % "test",
    Dependencies.scalatest % "test",
    Dependencies.junit % "test",
    Dependencies.logbackCore % "test",
    Dependencies.logbackClassic % "test"
  ),
  exports = Seq("", "protocol")
)

lazy val blendedTestsupport = BuildHelper.blendedProject(
  pName = "blended.testsupport",
  pDescription = Some("Some test helper classes."),
  deps = Seq(
    Dependencies.akkaActor,
    Dependencies.akkaCamel,
    Dependencies.akkaTestkit,
    Dependencies.camelCore,
    Dependencies.scalatest % "test",
    Dependencies.junit % "test",
    Dependencies.logbackCore % "test",
    Dependencies.logbackClassic % "test"
  )
).dependsOn(blendedUtil)
