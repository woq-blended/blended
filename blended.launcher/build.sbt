import sbt.Keys._

enablePlugins(SbtOsgi)

val namespace = "blended.launcher"

name := namespace
description := "Provide an OSGi Launcher"
libraryDependencies ++= Seq(
  Dependencies.orgOsgi,
  Dependencies.cmdOption
)

BlendedBundle(
  exportPackage = Seq(
    namespace
  ),
  importPackage = Seq(
    "org.apache.commons.daemon;resolution:=optional",
    // only used as cmdline app to parse cmdline params
    "de.tototec.cmdoption.*;resolution:=optional",
    "*"
  ),
  privatePackage = Seq(
    namespace + ".jvmrunner",
    namespace + ".runtime"
  )
)



