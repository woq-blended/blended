import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedSecuritySpray,
  packaging = "bundle",
  description = "Some security aware spray routes for the blended container.",
  dependencies = Seq(
    blendedSprayApi,
    blendedSpray,
    blendedAkka,
    scalaLib,
    orgOsgi,
    orgOsgiCompendium,
    slf4j,
    geronimoServlet30Spec,
    apacheShiroCore
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin
  )
)
