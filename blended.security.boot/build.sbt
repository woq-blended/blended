import sbt._
import sbt.Keys._

val namespace = "blended.security.boot"

enablePlugins(SbtOsgi)
name := namespace

description := "Container wide security classes."

libraryDependencies ++= Seq(
  Dependencies.orgOsgi
)

BlendedBundle(
  exportPackage = Seq(
    namespace
  )
)