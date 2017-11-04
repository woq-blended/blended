import sbt.Keys._

name := "blended.launcher"

description := "Provide an OSGi Launcher"

BuildHelper.bundleSettings(
  exportPkgs = Seq(""),
  importPkgs = Seq("org.apache.commons.daemon;resolution:=optional", "de.tototec.cmdoption.*;resolution:=optional"),
  privatePkgs = Seq("jvmrunner", "runtime")
)

libraryDependencies ++= Seq(
  Dependencies.orgOsgi,
  Dependencies.cmdOption
)

enablePlugins(SbtOsgi)