import sbt.Keys._

enablePlugins(SbtOsgi)

val namespace = "blended.container.context.api"

name := namespace
description := "Provide the container context API."


libraryDependencies ++= Seq(
  Dependencies.typesafeConfig,
  Dependencies.log4s,
  Dependencies.slf4j,
  Dependencies.julToSlf4j,
  Dependencies.scalatest % "test",
  Dependencies.logbackCore % "test",
  Dependencies.logbackClassic % "test",
  Dependencies.mockitoAll % "test"
)

BlendedBundle(
  exportPackage = Seq(
    namespace
  ),
  importPackage = Seq(
    "*"
  )
)
