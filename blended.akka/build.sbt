import sbt.Keys._

name := "blended.akka"

description := "The main bundle to provide an Actor based interface to the main OSGI services."

BuildHelper.bundleSettings(exports = Seq("", "protocol"))

OsgiKeys.bundleActivator := Some(name.value + ".internal.BlendedAkkaActivator")

libraryDependencies ++= Seq(
  Dependencies.akkaActor,
  Dependencies.akkaOsgi
)

enablePlugins(SbtOsgi)
