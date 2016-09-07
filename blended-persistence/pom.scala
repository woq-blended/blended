import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-versions.scala
#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedPersistence,
  packaging = "bundle",
  description = "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles.",
  dependencies = Seq(
    scalaLib % "provided",
    blendedAkka,
    domino,
    sprayJson,
    slf4j,
    scalaTest % "test",
    blendedTestSupport % "test",
    mockitoAll % "test",
    slf4jLog4j12 % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin
  )
)
