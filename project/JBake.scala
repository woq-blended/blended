import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties

import sbt.Keys._
import sbt._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

object JBake extends AutoPlugin {

  private val jbakeVersion = "2.6.3"
  private val jbakeUrl = s"https://dl.bintray.com/jbake/binary/jbake-${jbakeVersion}-bin.zip"

  object autoImport {
    val jbakeInputDir = settingKey[File]("The input directory for the site generation.")
    val jbakeOutputDir = settingKey[File]("The directory for the generated site.")
    val jbakeNodeBinDir = settingKey[File]("The directory where we can find the executables for node modules.")
    val jbakeMode = settingKey[String]("Run JBake in build or serve mode, default: build")

    val jbakeAsciidocAttributes = settingKey[Map[String, String]]("Asciidoctor attribute to passed to Asciidoctor")
    val jbakeSiteAssets = settingKey[Map[File, File]]("Assets to be included in the site")

    val jbakeBuild = taskKey[Seq[File]]("Run the jbake build step.")
    val jbakeSite = taskKey[Seq[File]]("Build the complete site")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = inConfig(Compile)(Seq(
    jbakeInputDir := baseDirectory.value,
    jbakeOutputDir := target.value / "site",
    jbakeMode := "build",
    jbakeSiteAssets := Map.empty,

    jbakeNodeBinDir := baseDirectory.value / "doc" / "target" / "scala-2.12" / "scalajs-bundler" / "main" / "node_modules" / ".bin",

    jbakeAsciidocAttributes := Map(
      "imagesdir" -> "images",
      "imagesoutdir"-> "images",
      "mermaid" -> jbakeNodeBinDir.value.getAbsolutePath()
    ),

    jbakeBuild := {

      val log = streams.value.log
      val jbakeDir = target.value / s"jbake-$jbakeVersion-bin"

      val jbakeLib = jbakeDir / "lib" / s"jbake-core-$jbakeVersion.jar"
      if (!jbakeLib.exists()) {
        log.info(s"Downloading jbake from [$jbakeUrl]")
        IO.unzipURL(new URL(jbakeUrl), target.value)
      }

      SiteGenerator(
        jbakeDir = jbakeDir,
        inputDir = jbakeInputDir.value,
        outputDir = jbakeOutputDir.value,
        nodeBinDir = jbakeNodeBinDir.value,
        attributes = jbakeAsciidocAttributes.value,
        mode = jbakeMode.value,
      )(streams.value.log).bake()
    },

    jbakeSite := {
      val site = jbakeBuild.dependsOn(BlendedDocsJs.project/Compile/fastOptJS/webpack).value

      val log = streams.value.log

      jbakeSiteAssets.value.foreach { case (from, to) =>
        if (from.exists()) {
          if (from.isDirectory()) {
            log.info(s"Copying directory from [$from] to [$to]")
            IO.copyDirectory(from, to)
          } else {
            log.info(s"Copying file from [$from] to [$to]")
            IO.copyFile(from, to)
          }
        }
      }

      site
    }
  ))
}

case class SiteGenerator(
  jbakeDir : File,
  inputDir : File,
  outputDir : File,
  nodeBinDir : File,
  mode : String,
  attributes : Map[String, String]
)(implicit log : Logger) {

  val jbakeCp = (jbakeDir / "lib").listFiles(new FileFilter{
    override def accept(pathname: File): Boolean = pathname.isDirectory() || (pathname.isFile && pathname.getName().endsWith("jar"))
  }).map(_.getAbsolutePath()).mkString(File.pathSeparator)

  def bake() : Seq[File] = {

    val props = new Properties()
    props.load(new FileInputStream(inputDir / "jbake.properties.tpl"))

    val attributeString = attributes.map{ case (k,v) => s"$k=$v" }.mkString(",")
    props.put("asciidoctor.attributes", attributeString)

    val os = new FileOutputStream(inputDir / "jbake.properties")
    props.store(os, "Auto generated jbake properties. Perform modifications in [jbake.properties.tpl]")

    IO.copyFile(inputDir / "logback.xml", jbakeDir / "lib" / "logging" / "logback.xml")

    val currPath = System.getenv("PATH")

    val jBakeOptions = ForkOptions()
      .withEnvVars(Map("PATH" -> s"${nodeBinDir.getAbsolutePath()}${File.pathSeparator}$currPath"))

    val args : Seq[String] = Seq(
      "-classpath", jbakeCp,
      "org.jbake.launcher.Main",
      inputDir.getAbsolutePath(),
      outputDir.getAbsolutePath(),
      "-b"
    ) ++ (
      if (mode.equalsIgnoreCase("build")) Seq() else Seq("-s")
    )

    log.info("Running jbake with arguments\n" + args.mkString("\n"))
    val process = Fork.java.fork(jBakeOptions, args)

    process.exitValue() match {
      case 0 =>
        IO.touch(outputDir / ".nojekyll")
        Seq(outputDir)
      case v =>
        throw new Exception(s"JBake ended with return code [$v]")
    }
  }
}
