import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedMgmtBase,
  packaging = "bundle",
  description = "Shared classes for management and reporting facility.",
  dependencies = Seq(
    scalaLib % "provided",
    blendedDomino,
    blendedUpdaterConfig,
    sprayJson,
    scalaTest % "test"
  ), 
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin
  )
)
