import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedScep,
  packaging = "bundle",
  description = "Bundle to manage the container certificate via SCEP.",
  dependencies = Seq(
    scalaLib % "provided",
    scalaReflect % "provided",
    slf4j,
    scep,
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
