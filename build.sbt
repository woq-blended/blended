import sbt.Keys._
import sbt._

val m2Repo = "file://" + System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository")

lazy val defaultSettings : Seq[Def.SettingsDefinition] = Seq(
  organization := BlendedVersions.blendedGroupId,
  version := BlendedVersions.blended,

  scalaVersion := BlendedVersions.scala,
  sourcesInBase := false
)

lazy val root = project
  .in(file("."))
  .settings(defaultSettings:_*)
  .settings(
    name := "blended"
  )
  .aggregate(blendedUtil)

lazy val blendedUtil = project
  .in(file("blended.util"))
  .settings(defaultSettings:_*)
  .settings(
    name := "blended.util",

    libraryDependencies ++= Seq(
      Dependencies.akkaActor,
      Dependencies.slf4j,
      Dependencies.akkaTestkit % "test",
      Dependencies.scalatest % "test",
      Dependencies.junit % "test"
    )
  )
