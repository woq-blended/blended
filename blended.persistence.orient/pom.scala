import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

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
    Dependency(
      blendedTestSupport,
      scope = "test",
      exclusions = Seq("*" % "*")
    ),
    mockitoAll % "test",
    logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin
  )
)
