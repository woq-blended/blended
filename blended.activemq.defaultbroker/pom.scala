import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedActivemqDefaultbroker,
  packaging = "bundle",
  description = "An Active MQ broker instance.",
  dependencies = Seq(
    blendedActivemqBrokerstarter
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin
  )
)

