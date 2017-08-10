val appName = "blended.mgmt.ui"

lazy val root = project
  .in(file("."))
  .settings(
    organization := BlendedVersions.blendedGroupId,
    version := BlendedVersions.blendedVersion,
    name := appName,
    scalaVersion := BlendedVersions.scalaVersion,
    sourcesInBase := false,
    mainClass in Compile := Some(s"$appName.MgmtConsole")
  )
  .enablePlugins(ScalaJSPlugin, SbtWeb)