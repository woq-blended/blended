import sbt.Keys._

enablePlugins(SbtOsgi)

val namespace = "blended.mgmt.base"

name := namespace
description := "Shared classes for management and reporting facility."

libraryDependencies ++= Seq(
  Dependencies.prickle,
)

BlendedBundle(
  bundleActivator = namespace + ".internal.MgmtActivator",
  exportPackage = Seq(
    namespace,
    namespace + ".json"
  )
)


