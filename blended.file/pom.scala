import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedFile,
  packaging = "bundle",
  description = "Bundle to define a customizable Filedrop / Filepoll API.",
  dependencies = Seq(
    blendedAkka,
    blendedTestSupport % "test",
    akkaTestkit % "test",
    akkaSlf4j % "test",
    scalaTest % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin
  )
)
