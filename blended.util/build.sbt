import sbt._
import sbt.Keys._

name := "blended.util"
description := "Utility classes to use in other bundles."

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

BuildHelper.bundleSettings(
  exportPkgs = Seq("", "protocol")
)

enablePlugins(SbtOsgi)