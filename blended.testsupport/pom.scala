import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.testSupport,
  packaging = "jar",
  description = "Some test helper classes.",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Blended.containerContextApi,
    Blended.util,
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
