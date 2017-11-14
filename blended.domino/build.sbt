import sbt.Keys._

enablePlugins(SbtOsgi)
val namespace = "blended.domino"

name := namespace
description := "Blended Domino extension for new Capsule scopes."

libraryDependencies ++= Seq(
  Dependencies.typesafeConfig
)

BlendedBundle(
  exportPackage = Seq(
    namespace
  )
)

