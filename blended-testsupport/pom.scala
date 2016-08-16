import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedTestSupport,
  packaging = "jar",
  description = "Some test helper classes.",
  dependencies = Seq(
    scalaLib % "provided",
    blendedUtil,
    junit,
    camelCore,
    slf4j,
    slf4jLog4j12,
    akkaTestkit,
    scalaTest
  ),
  plugins = Seq(
      scalaMavenPlugin,
      scalatestMavenPlugin
  )
)
