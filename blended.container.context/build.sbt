import sbt.Keys._

name := "blended.container.context"

description := "A simple OSGI service to provide access to the container's config directory."

BuildHelper.bundleSettings(
  exports = Seq(""),
  imports = Seq("blended.launcher.runtime;resolution:=optional")
)

OsgiKeys.bundleActivator := Some(name.value + ".internal.ContainerContextActivator")

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

enablePlugins(SbtOsgi)