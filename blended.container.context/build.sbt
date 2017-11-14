import sbt.Keys._

enablePlugins(SbtOsgi)

val namespace = "blended.container.context"

name := namespace
description := "A simple OSGI service to provide access to the container's config directory."


libraryDependencies ++= Seq(
  Dependencies.typesafeConfig,
  Dependencies.slf4j,
  Dependencies.julToSlf4j,
  Dependencies.domino,
  Dependencies.orgOsgi,
  Dependencies.scalatest % "test",
  Dependencies.logbackCore % "test",
  Dependencies.logbackClassic % "test",
  Dependencies.mockitoAll % "test"
)

BlendedBundle(
  bundleActivator = namespace + ".internal.ContainerContextActivator",
  exportPackage = Seq(
    namespace
  ),
  importPackage = Seq(
    "blended.launcher.runtime;resolution:=optional",
    "*"
  )
)
