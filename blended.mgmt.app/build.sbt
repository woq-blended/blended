import scalajsbundler.BundlingMode.LibraryOnly

lazy val akkajsVersion = "1.2.5.11"
lazy val reactVersion = "16.2.0"
lazy val m2Repo = "file://" + System.getProperty("maven.repo.local", System.getProperty("user.home") + "/.m2/repository")


lazy val root = project
  .in(file("."))
  .settings(commonSettings, npmSettings)
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)

lazy val commonSettings = Seq(
  scalaVersion := "2.12.5",
  name := "blended.mgmt.app",
  version := BlendedVersions.blendedVersion,
  organization := BlendedVersions.blendedGroupId,
  licenses += ("Apache 2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),

  resolvers += "Local Maven Repository" at m2Repo,

  scalaJSUseMainModuleInitializer := true,

  sourcesInBase := false,

  artifactPath.in(Compile, fastOptJS) := ((crossTarget in (Compile, fastOptJS)).value /
    ((moduleName in fastOptJS).value + "-opt.js")),

  webpackResources :=
    webpackResources.value +++ PathFinder(Seq(baseDirectory.value / "index.html")) ** "*.*",

  webpackConfigFile in (Test) := Some(baseDirectory.value / "webpack.config.test.js"),
  webpackConfigFile in (Compile, fastOptJS) := Some(
    baseDirectory.value / "webpack.config.dev.js"),
  webpackConfigFile in (Compile, fullOptJS) := Some(
    baseDirectory.value / "webpack.config.prod.js"),

  jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv,

  webpackBundlingMode := LibraryOnly(),
  emitSourceMaps := false,

  parallelExecution in ThisBuild := false,

  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.5",
    "org.akka-js" %%% "akkajsactor" % akkajsVersion,
    "com.github.ahnfelt" %%% "react4s" % "0.9.8-SNAPSHOT",
    "com.github.werk" %%% "router4s" % "0.1.1-SNAPSHOT",
    organization.value %%% "blended.updater.config" % BlendedVersions.blendedVersion,
    "com.github.benhutchison" %%% "prickle" % BlendedVersions.prickle,

    "org.scalatest" %%% "scalatest" % "3.0.5" % "test",
    "org.akka-js" %%% "akkajstestkit" % akkajsVersion % "test"
  ),

  // Important: Also add the basedirectory here, otherwise the index.html won't be visible in the webpack dev server
  unmanagedSourceDirectories.in(Compile) := Seq(scalaSource.in(Compile).value),
  unmanagedSourceDirectories.in(Test) := Seq(scalaSource.in(Test).value)
)

lazy val npmSettings = Seq(
  useYarn := true,
  npmDependencies.in(Compile) := Seq(
    "react" -> "16.2.0",
    "react-dom" -> "16.2.0",
    "jsdom" -> "11.8.0"
  )
)