import java.io.File

import sbt.Keys._
import sbt._

object JBake extends AutoPlugin {

  private val jbakeVersion = "2.6.3"
  private val jbakeUrl = s"https://dl.bintray.com/jbake/binary/jbake-${jbakeVersion}-bin.zip"

  object autoImport {
    val jbakeInputDir = settingKey[File]("The input directory for the site generation.")
    val jbakeOutputDir = settingKey[File]("The directory for the generated site.")
    val jbakeClearCache = settingKey[Boolean]("Whether to clear the JBake cache before building the site.")
    val jbakeProperties = settingKey[Map[String, Any]]("Additional properties to configure JBake")

    val jbakeBuild = taskKey[Seq[File]]("Run the jbake build step.")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = inConfig(Compile)(Seq(
    jbakeInputDir := baseDirectory.value / "doc",
    jbakeOutputDir := target.value / "site",
    jbakeClearCache := true,
    jbakeProperties := Map.empty,

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
        outputDir = jbakeOutputDir.value
      )(streams.value.log).bake()
    }
  ))
}

case class SiteGenerator(
  jbakeDir : File,
  inputDir : File,
  outputDir : File
)(implicit log : Logger) {

  val jbakeCp = (jbakeDir / "lib").listFiles(new FileFilter{
    override def accept(pathname: File): Boolean = pathname.isDirectory() || (pathname.isFile && pathname.getName().endsWith("jar"))
  }).map(_.getAbsolutePath()).mkString(File.pathSeparator)


  def bake() : Seq[File] = {

    val jBakeOptions = ForkOptions()

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

//    try {
//      //Orient.instance().startup()
//      val oven = new Oven(jbakeConfig)
//      oven.bake()
//    } catch {
//      case t : Throwable =>
//        t.printStackTrace()
//        log.err(s"Site generation failed : [${t.getMessage()}]")
//    }

    Seq.empty
  }
}

//// Dependencies for JBake
//"org.jbake" % "jbake-core" % "2.6.3",
//("org.asciidoctor" % "asciidoctorj" % "1.5.7").withExclusions(
//  Vector(
//    InclExclRule().withOrganization("com.github.jnr")
//  )
//),
//("org.asciidoctor" % "asciidoctorj-diagram" % "1.5.10").withExclusions(
//  Vector(
//    InclExclRule().withOrganization("com.github.jnr")
//  )
//),
//"org.thymeleaf" % "thymeleaf" % "3.0.9.RELEASE",
//"de.neuland-bfi" % "jade4j" % "1.2.7",
//"org.codehaus.groovy" % "groovy-templates" % "2.5.3",
//"org.codehaus.groovy" % "groovy-dateutil" % "2.5.3",
//"org.freemarker" % "freemarker" % "2.3.28",
////  ("org.jruby" % "jruby-complete" % "9.2.0.0").withExclusions(
////    Vector(
////      InclExclRule().withOrganization("com.github.jnr")
////    )
////  ),
//("com.github.jnr" % "jnr-constants" % "0.9.5").intransitive(),
////  ("com.github.jnr" % "jnr-posix" % "3.0.12").intransitive()
