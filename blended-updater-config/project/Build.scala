object MgmtUiBuild extends Build {

  val appName = "blended.mgmt.ui"

  lazy val root =
    project.in(file("."))
      .settings(projectSettings: _*)
      .enablePlugins(ScalaJSPlugin, SbtWeb)

  lazy val projectSettings = Seq(
    organization := "de.wayofquality.blended",
    version := Versions.app,
    name := appName,
    scalaVersion := Versions.scala,

    sourcesInBase := false,
    mainClass in Compile := Some("blended.mgmt.ui.MgmtConsole"),

    LessKeys.sourceMap in Assets := true,      // generate a source map for developing in the browser
    LessKeys.compress in Assets := true,       // Compress the final CSS
    LessKeys.color in Assets := true,          // Colorise Less output
    LessKeys.sourceMapLessInline := false,     // Have less files extra

    (sourceDirectory in Assets) := (baseDirectory.value / "src" / "main" / "less"),
    includeFilter in (Assets, LessKeys.less) := "main.less",
    (compile in Compile) <<= (compile in Compile) dependsOn (LessKeys.less in Compile),

    libraryDependencies ++= Dependencies.clientDeps.value,
    jsDependencies ++= Dependencies.jsDependencies.value,
    persistLauncher in Compile := true,
    persistLauncher in Test := false
  )


  object Dependencies {

    lazy val clientDeps = Def.setting(Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % Versions.scalajsReact,
      "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom,
      //      "com.lihaoyi" %%% "upickle" % Versions.upickle,
      //      "com.lihaoyi" %%% "scalatags" % Versions.scalaTags,
      //      "be.doeraene" %%% "scalajs-jquery" % Versions.scalajsJQuery,

      "com.github.japgolly.scalajs-react" %%% "test" % Versions.scalajsReact % "test"

    ))

    lazy val jsDependencies = Def.setting(Seq(

      "org.webjars" % "bootstrap" % Versions.bootstrap / "bootstrap.js",
      "org.webjars.bower" % "react" % Versions.react / "react.js"

    ))
  }
}
