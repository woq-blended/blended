val appName = "blended.updater.config"

lazy val root =
  project.in(file("."))
    .settings(projectSettings: _*)
    .enablePlugins(ScalaJSPlugin)

lazy val projectSettings = Seq(
  organization := BlendedVersions.blendedGroupId,
  version := BlendedVersions.blendedVersion,
  name := appName,
  scalaVersion := BlendedVersions.scalaVersionJS,
  moduleName := appName,

  // avoid picking up pom.scala as source file
  sourcesInBase := false,

  libraryDependencies ++= Seq(
    "com.github.benhutchison" %%% "prickle" % BlendedVersions.prickle,
    "org.scalatest" %%% "scalatest" % BlendedVersions.scalaTestVersion % "test"
  ),

  (unmanagedSourceDirectories in Compile) := Seq(
    baseDirectory.value / "shared" / "main" / "scala"
  ),

  (unmanagedSourceDirectories in Test) := Seq(
    baseDirectory.value / "shared" / "test" / "scala"
  )
)
