import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-versions.scala
#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  blendedPersistenceOrient,
  packaging = "bundle",
  description = "Implement a persistence backend with OrientDB.",
  dependencies = Seq(
    scalaLib % "provided",
    blendedPersistence,
    blendedAkka,
    slf4j,
    domino,
    orientDbCore,
    scalaTest % "test",
    blendedTestSupport % "test",
    mockitoAll % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin
  )
)
