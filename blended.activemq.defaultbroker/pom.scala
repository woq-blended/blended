import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-common.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala

BlendedModel(
  gav = blendedActivemqDefaultbroker,
  packaging = "bundle",
  description = "An Active MQ broker instance.",
  dependencies = Seq(
    blendedActivemqBrokerstarter
  ),
  build = Build(
    plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin
    )
  )
)

