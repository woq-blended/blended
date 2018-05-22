import sbt._
import sbt.Keys._

enablePlugins(SbtOsgi)

val namespace = "blended.util"

name := namespace
description := "Utility classes to use in other bundles."

libraryDependencies ++= Seq(
  Dependencies.akkaActor,
  Dependencies.log4s,
  Dependencies.slf4j,
  Dependencies.akkaTestkit % "test",
  Dependencies.akkaSlf4j % "test",
  Dependencies.scalatest % "test",
  Dependencies.junit % "test",
  Dependencies.logbackCore % "test",
  Dependencies.logbackClassic % "test"
)

BlendedBundle(
  exportPackage = Seq(
    namespace,
    namespace + ".protocol"
  )
)

