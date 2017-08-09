import java.nio.file.{Files, Paths, StandardCopyOption}

import com.typesafe.sbt.less.Import._
import com.typesafe.sbt.web.SbtWeb
import com.typesafe.sbt.web.SbtWeb.autoImport._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._

object Build extends sbt.Build {

  val warDir = SettingKey[File]("warDir", "The base directory where the exploded war file is located.")
  val compiledJS = TaskKey[Seq[File]]("The complete list of compiled Javascript files.")
  val cleanCSS = TaskKey[Unit]("Remove old CSS files")
  val copyCSS = taskKey[Unit]("Copy compiled CSS to jetty folder")
  val copyJS = taskKey[Unit]("Copy compiled JS to jetty folder")

  val appName = "blended.mgmt.ui"

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

  lazy val root =
    project.in(file("."))
      .settings(projectSettings: _*)
      .enablePlugins(ScalaJSPlugin, SbtWeb)

  lazy val projectSettings = Seq(
    organization := BlendedVersions.blendedGroupId,
    version := BlendedVersions.blendedVersion,
    name := appName,
    scalaVersion := BlendedVersions.scalaVersion,

    sourcesInBase := false,
    mainClass in Compile := Some(s"$appName.MgmtConsole"),

    LessKeys.sourceMap in Assets := true,      // generate a source map for developing in the browser
    LessKeys.compress in Assets := false,      // Compress the final CSS
    LessKeys.color in Assets := true,          // Colorise Less output
    LessKeys.sourceMapLessInline := false,     // Have less files extra
    LessKeys.verbose := true,
    LessKeys.cleancss := true,

    (sourceDirectory in Assets) := (baseDirectory.value / "src" / "main" / "less"),
    includeFilter in (Assets, LessKeys.less) := "main.less",

    libraryDependencies ++= Dependencies.clientDeps.value,
    jsDependencies ++= Dependencies.jsDependencies.value,
    persistLauncher in Compile := true,
    persistLauncher in Test := false,

    resolvers += Resolver.mavenLocal,

    warDir := new File(baseDirectory.value / "target" / appName + "-" + BlendedVersions.blendedVersion),

    (cleanCSS in Compile) := removeRecursive(baseDirectory.value / "target" / "web" / "less"),

    (copyCSS in Compile) := copyFiles((LessKeys.less in Compile).value, warDir.value / "css"),

    (copyJS in Compile) := copyFiles(jsFiles(new File((baseDirectory.value / "target" / "scala-2.11").getAbsolutePath())), warDir.value / "scripts"),

    // After compilation we copy all the generated JavaScript Files and css files to
    // the jetty directory of the outer maven project to allow the changes being picked
    // up on the fly.

    (LessKeys.less in Compile) <<= (LessKeys.less in Compile).dependsOn(cleanCSS in Compile),
    (compile in Compile) <<= (compile in Compile).dependsOn(LessKeys.less in Compile),
    (copyCSS in Compile) <<= (copyCSS in Compile).triggeredBy(LessKeys.less in Compile),
    (copyJS in Compile) <<= (copyJS in Compile).triggeredBy(fastOptJS in Compile)

  )

  object Dependencies {

    lazy val clientDeps = Def.setting(Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % Versions.scalajsReact,
      "com.github.japgolly.scalajs-react" %%% "extra" % Versions.scalajsReact,
      "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom,
      organization.value %%% "blended.updater.config" % BlendedVersions.blendedVersion,
      "com.github.benhutchison" %%% "prickle" % BlendedVersions.prickle,

      "com.github.japgolly.scalajs-react" %%% "test" % Versions.scalajsReact % "test",
      "org.scalatest" %%% "scalatest" % BlendedVersions.scalaTestVersion % "test"
    ))

    lazy val jsDependencies = Def.setting(Seq(

      "org.webjars" % "bootstrap" % Versions.bootstrap / "bootstrap.js",
      "org.webjars.bower" % "react" % Versions.react / "react.js",
      "org.webjars.npm" % "react-motion" % Versions.reactMotion / "build/react-motion.js",
      RuntimeDOM,
      "org.webjars" % "jquery" % "2.1.4" / "2.1.4/jquery.js"
    ))
  }


}

