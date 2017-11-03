import sbt.Keys._

name := "blended.mgmt.base"

description := "Shared classes for management and reporting facility."

BuildHelper.bundleSettings(exports = Seq("", "json"))

OsgiKeys.bundleActivator := Some(name.value + ".internal.MgmtActivator")

libraryDependencies ++= Seq(
  Dependencies.prickle,
)

enablePlugins(SbtOsgi)
