import sbt.Keys._

enablePlugins(SbtOsgi)

val namespace = "blended.container.context.impl"

name := namespace
description := "Provide the container context implementation."

libraryDependencies ++= Seq(
  Dependencies.orgOsgi,
  Dependencies.typesafeConfig,
  Dependencies.log4s,
  Dependencies.slf4j,
  Dependencies.julToSlf4j,
  Dependencies.scalatest % "test",
  Dependencies.logbackCore % "test",
  Dependencies.logbackClassic % "test",
  Dependencies.mockitoAll % "test"
)

libraryDependencies += Dependencies.domino exclude("org.osgi", "org.osgi.core")

BlendedBundle(
  bundleActivator = s"${namespace}.internal.ContainerContextActivator",
  importPackage = Seq(
    "*"
  )
)
