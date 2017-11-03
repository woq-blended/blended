import sbt.Keys._

name := "blended.launcher"

description := "Provide an OSGi Launcher"

BuildHelper.bundleSettings(
  exports = Seq(""),
  imports = Seq("org.apache.commons.daemon;resolution:=optional", "de.tototec.cmdoption.*;resolution:=optional"),
  privates = Seq("jvmrunner", "runtime")
)

libraryDependencies ++= Seq(
  Dependencies.orgOsgi,
  Dependencies.cmdOption
)

enablePlugins(SbtOsgi)