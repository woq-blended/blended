import java.nio.file.{Files, Paths, StandardCopyOption}

import sbt.Keys._
import sbt._

val appName = "blended.mgmt.ui"

val warDir = SettingKey[File]("warDir", "The base directory where the exploded war file is located.")

val compiledJS = TaskKey[Seq[File]]("The complete list of compiled Javascript files.")

val cleanCSS = TaskKey[Unit]("Remove old CSS files")

val copyCSS = taskKey[Unit]("Copy compiled CSS to jetty folder")

val copyJS = taskKey[Unit]("Copy compiled JS to jetty folder")

def removeRecursive(f: File) : Unit = {

  if (f.exists()) {
    f.listFiles().toList.foreach { file =>
      if (file.canWrite()) {
        if (file.isFile()) {
          println(s"Removing file [${file.getAbsolutePath()}]")
          file.delete()
        } else if (file.isDirectory()) {
          removeRecursive(file)
        }
      }
    }
  }

  println(s"Removing directory [${f.getAbsolutePath()}]")
  f.delete()
}

def jsFiles(dir: File) : Seq[File] = {

  val ff = new FileFilter {
    override def accept(f: File) =
      f.isFile() && f.getName().startsWith("blended-mgmt-ui") && (f.getName().indexOf("js") != -1)
  }

  dir.listFiles(ff)
}

def copyFiles(files: Seq[File], target: File) : Unit = {

  println(s"Copying [${files.size}] files to [$target]")

  if (!target.exists()) {
    target.mkdirs()
  }

  Option(files).foreach{ _ map { f =>
    val pSrc = Paths.get(f.getAbsolutePath())
    val pTgt = Paths.get(target.getAbsolutePath(), f.getName())
    Files.copy(pSrc, pTgt, StandardCopyOption.REPLACE_EXISTING)

    pTgt.toFile()
  }}
}

lazy val root = project
  .in(file("."))
  .settings(
    organization := BlendedVersions.blendedGroupId,
    version := BlendedVersions.blendedVersion,
    name := appName,

    scalaJSUseMainModuleInitializer := true,

    scalaVersion := BlendedVersions.scalaVersion,
    sourcesInBase := false,
    mainClass in Compile := Some(s"$appName.MgmtConsole"),

    resolvers += Resolver.mavenLocal,

    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % Versions.scalajsReact,
      "com.github.japgolly.scalajs-react" %%% "extra" % Versions.scalajsReact,
      "com.github.japgolly.scalacss" %%% "ext-react" % Versions.scalaCss,

      "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom,
      organization.value %%% "blended.updater.config" % BlendedVersions.blendedVersion,
      "com.github.benhutchison" %%% "prickle" % BlendedVersions.prickle,
      "com.olvind" %%% "scalajs-react-components" % "0.7.0",

      "com.github.japgolly.scalajs-react" %%% "test" % Versions.scalajsReact % "test",
      "org.scalatest" %%% "scalatest" % BlendedVersions.scalaTestVersion % "test"
    ),

    npmDependencies in Compile ++= Seq(
      "react" -> Versions.react,
      "react-dom" -> Versions.react,
      "react-motion" -> Versions.reactMotion,
      "react-split-pane" -> Versions.splitPane,
      "jquery" -> Versions.jQuery,
      "bootstrap" -> Versions.bootstrap
    ),

    // Add a dependency to the expose-loader (which will expose react to the global namespace)
    npmDevDependencies in Compile += "expose-loader" -> "0.7.1",

    LessKeys.sourceMap in Assets := true,      // generate a source map for developing in the browser
    LessKeys.compress in Assets := false,      // Compress the final CSS
    LessKeys.color in Assets := true,          // Colorise Less output
    LessKeys.sourceMapLessInline := false,     // Have less files extra
    LessKeys.verbose := true,
    LessKeys.cleancss := true,

    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "blended.mgmt.ui.webpack.js"),
    enableReloadWorkflow := true,
    emitSourceMaps := true,

    (sourceDirectory in Assets) := (baseDirectory.value / "src" / "main" / "less"),
    includeFilter in (Assets, LessKeys.less) := "main.less",

    warDir := new File(baseDirectory.value / "target" / appName + "-" + BlendedVersions.blendedVersion),

    (LessKeys.less in Compile) := {
      (LessKeys.less in Compile).value
    },

    (compile in Compile) := {
      val css = (LessKeys.less in Compile).value
      val r = (compile in Compile).value
      copyFiles(css, warDir.value / "css")
      r
    }

  )
  .enablePlugins(ScalaJSPlugin, SbtWeb, ScalaJSBundlerPlugin)