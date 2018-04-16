import scalajsbundler.BundlingMode.LibraryOnly

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

  scalaJSUseMainModuleInitializer := true,

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

  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.1",
    "com.github.japgolly.scalajs-react" %%% "core" % "1.2.0",
    "com.github.japgolly.scalajs-react" %%% "extra" % "1.2.0"
  ),

  // Important: Also add the basedirectory here, otherwise the index.html won't be visible in the webpack dev server
  unmanagedSourceDirectories.in(Compile) := Seq(scalaSource.in(Compile).value, baseDirectory.value),
  unmanagedSourceDirectories.in(Test) := Seq(scalaSource.in(Test).value, baseDirectory.value)
)


lazy val npmSettings = Seq(
  useYarn := true,
  npmDependencies.in(Compile) := Seq(
    "react" -> "16.2.0",
    "react-dom" -> "16.2.0"
  )
)