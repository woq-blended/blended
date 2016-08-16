import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedContainerContext,
  packaging = "bundle",
  description = "A simple OSGI service to provide access to the container's config directory.",
  dependencies = Seq(
    scalaLib % "provided",
    blendedUtil,
    blendedLauncher,
    orgOsgiCompendium,
    orgOsgi,
    domino,
    slf4j,
    julToSlf4j
  ),
  plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin
  )
)
