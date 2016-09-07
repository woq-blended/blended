import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-versions.scala
#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  blendedDomino,
  packaging = "bundle",
  description = "Blended Domino extension for new Capsule scopes.",
  dependencies = Seq(
    domino,
    typesafeConfig,
    blendedContainerContext,
    scalaLib
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin
  )
)
