import sbt.Keys._
import blended.sbt.BlendedPlugin.autoImport._

name := "blended.spray.api"

description := "Package the complete Spray API into a bundle."

libraryDependencies ++= Seq(
  Dependencies.sprayServlet,
  Dependencies.sprayClient,
  Dependencies.sprayRouting,
  Dependencies.sprayJson,
  Dependencies.sprayCaching,
  Dependencies.shapeless,
  Dependencies.concurrentLinkedHashMapLru
)

excludeDependencies in (Compile, packageBin) ++= Seq(
  ExclusionRule(organization = Dependencies.scalaLibrary.organization),
  ExclusionRule(organization = "org.scala-lang.modules")
)

packageBin in (Compile) := {
  (packageBin in Compile).value
  OsgiKeys.bundle.value
}

OsgiKeys.embeddedJars := compileDeps.value

OsgiKeys.bundleSymbolicName := name.value

OsgiKeys.bundleVersion := version.value

OsgiKeys.importPackage := Seq(
  scalaRange.value,
  "com.sun.*;resolution:=optional",
  "sun.*;resolution:=optional",
  "net.liftweb.*;resolution:=optional",
  "play.*;resolution:=optional",
  "twirl.*;resolution:=optional",
  "org.json4s.*;resolution:=optional",
  "*"
)

OsgiKeys.additionalHeaders := Map[String, String](
  ("-exportcontents",
    "spray.*;version=" + Dependencies.sprayVersion + ";-split-package:=merge-first" +
      "akka.spray.*;version=" + Dependencies.sprayVersion + ";-split-package:=merge-first," +
      "org.parboiled.*;version=" + Dependencies.parboiledVersion + ";-split-package:=merge-first," +
      "shapeless.*;version=" + Dependencies.parboiledVersion + ";-split-package:=merge-first"
  )
)

OsgiKeys.privatePackage := Seq.empty

enablePlugins(SbtOsgi, BlendedPlugin)