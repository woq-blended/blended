import sbt.Keys._

enablePlugins(SbtOsgi)

val namespace = "blended.akka"

name := namespace
description := "The main bundle to provide an Actor based interface to the main OSGI services."

libraryDependencies ++= Seq(
  Dependencies.akkaActor,
  Dependencies.akkaOsgi
)

BlendedBundle(
  bundleActivator = namespace + ".internal.BlendedAkkaActivator",
  exportPackage = Seq(
    namespace,
    namespace + ".protocol"
  )
)
