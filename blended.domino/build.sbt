import sbt.Keys._

name := "blended.domino"

description := "Blended Domino extension for new Capsule scopes."

BuildHelper.bundleSettings(
  exports = Seq("")
)

libraryDependencies ++= Seq(
  Dependencies.typesafeConfig
)

enablePlugins(SbtOsgi)