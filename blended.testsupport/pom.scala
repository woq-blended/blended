import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedTestSupport,
  packaging = "jar",
  description = "Some test helper classes.",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.felixConnect,
    blendedUtil,
    Deps.junit,
    Deps.camelCore,
    slf4j,
    akkaTestkit,
    scalaTest,
    akkaCamel,
    slf4jLog4j12 % "test"
  ),
  plugins = Seq(
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
