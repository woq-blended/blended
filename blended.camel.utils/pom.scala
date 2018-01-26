import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedCamelUtils,
  packaging = "bundle",
  description = """Useful helpers for Camel""",
  dependencies = Seq(
    scalaLib % "provided",
    orgOsgi,
    orgOsgiCompendium,
    camelJms,
    slf4j,
    blendedAkka
  ),
  plugins = Seq(
    sbtCompilerPlugin,
    mavenBundlePlugin
  )
)
