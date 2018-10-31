import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties

import sbt.Keys._
import sbt._

object JBake extends AutoPlugin {

  private val jbakeVersion = "2.6.3"
  private val jbakeUrl = s"https://dl.bintray.com/jbake/binary/jbake-${jbakeVersion}-bin.zip"

  object autoImport {
    val jbakeInputDir = settingKey[File]("The input directory for the site generation.")
    val jbakeOutputDir = settingKey[File]("The directory for the generated site.")
    val jbakeNodeBinDir = settingKey[File]("The directory where we can find the executables for node modules.")

    val jbakeAsciidocAttributes = settingKey[Map[String, String]]("Asciidoctor attribute to passed to Asciidoctor")

    val jbakeBuild = taskKey[Seq[File]]("Run the jbake build step.")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = inConfig(Compile)(Seq(
    jbakeInputDir := baseDirectory.value / "doc",
    jbakeOutputDir := target.value / "site",

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
        attributes = jbakeAsciidocAttributes.value
      )(streams.value.log).bake()
    }
  ))
}

case class SiteGenerator(
  jbakeDir : File,
  inputDir : File,
  outputDir : File,
  nodeBinDir : File,
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

    val currPath = System.getenv("PATH")

    val jBakeOptions = ForkOptions()
      .withEnvVars(Map("PATH" -> s"${nodeBinDir.getAbsolutePath()}${File.pathSeparator}$currPath"))

    log.info("Running jbake")
    val process = Fork.java.fork(jBakeOptions, Seq(
      "-classpath", jbakeCp,
      "org.jbake.launcher.Main",
      inputDir.getAbsolutePath(),
      outputDir.getAbsolutePath(),
      "-b"
    ))

    val exitVal = process.exitValue()

    if (exitVal != 0) {
      throw new Exception(s"JBake ended with return code [$exitVal]")
    }

    Seq(outputDir)
  }
}
