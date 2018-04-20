import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.file,
  packaging = "bundle",
  description = "Bundle to define a customizable Filedrop / Filepoll API.",
  dependencies = Seq(
    Blended.akka,
    Blended.jmsUtils,
    Blended.testSupport % "test",
    akkaTestkit % "test",
    akkaSlf4j % "test",
    logbackCore % "test",
    logbackClassic % "test",
    scalaTest % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
