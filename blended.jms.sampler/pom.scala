import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedJmsSampler,
  packaging = "bundle",
  description = "A bundle to sample messages from a given JMS topic.",
  dependencies = Seq(
    blendedAkka,
    slf4j,
    blendedDomino,
    blendedUtil,
    geronimoJms11Spec
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin
  )
)
