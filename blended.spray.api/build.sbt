import sbt.Keys._

enablePlugins(SbtOsgi, BlendedPlugin)

name := "blended.spray.api"

description := "Package the complete Spray API into a bundle."

// exactly those deps we want to embed
libraryDependencies := Seq(
  Dependencies.sprayServlet,
  Dependencies.sprayClient,
  Dependencies.sprayRouting,
  Dependencies.sprayIo,
  Dependencies.sprayUtil,
  Dependencies.sprayJson,
  Dependencies.sprayCaching,
  Dependencies.sprayCan,
  Dependencies.sprayHttp,
  Dependencies.sprayHttpx,
  Dependencies.shapeless,
  Dependencies.concurrentLinkedHashMapLru,
  Dependencies.mimepull,
  Dependencies.parboiledScala,
  Dependencies.parboiledCore
).map(_ intransitive())

packageBin in (Compile) := {
  packageBin.in(Compile).value
  OsgiKeys.bundle.value
}

osgiSettings

OsgiKeys.embeddedJars := dependencyClasspath.in(Compile).value.files

OsgiKeys.bundleSymbolicName := name.value

OsgiKeys.bundleVersion := version.value

OsgiKeys.importPackage := Seq(
  "scala.*;version=\"[" + BlendedVersions.scalaBin + "," + BlendedVersions.scalaBin + ".50)\"",
  "com.sun.*;resolution:=optional",
  "sun.*;resolution:=optional",
  "net.liftweb.*;resolution:=optional",
  "play.*;resolution:=optional",
  "twirl.*;resolution:=optional",
  "org.json4s.*;resolution:=optional",
  "*"
)

OsgiKeys.additionalHeaders := Map[String, String](
  "-exportcontents" -> Seq(
    "spray.*;version=" + Dependencies.sprayVersion + ";-split-package:=merge-first",
    "akka.spray.*;version=" + Dependencies.sprayVersion + ";-split-package:=merge-first",
    "org.parboiled.*;version=" + Dependencies.parboiledVersion + ";-split-package:=merge-first",
    "shapeless.*;version=" + Dependencies.parboiledVersion + ";-split-package:=merge-first"
  ).mkString(",")
)

OsgiKeys.privatePackage := Seq()

