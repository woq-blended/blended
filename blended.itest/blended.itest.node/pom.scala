import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../../blended.build/build-versions.scala
//#include ../../blended.build/build-dependencies.scala
//#include ../../blended.build/build-plugins.scala
//#include ../../blended.build/build-common.scala

BlendedModel(
  gav = Blended.itestNode,
  packaging = "jar",
  description = "A sample integration test using docker to fire up the container(s) under test, execute the test suite and shutdown the container(s) afterwards.",
  dependencies = Seq(
    scalaLib,
    Blended.dockerDemoApacheDS % "provided",
    Blended.dockerDemoNode % "provided",
    activeMqClient % "test",
    Blended.itestSupport % "test",
    scalaTest % "test",
    slf4j % "test",
    akkaSlf4j % "test",
    logbackCore % "test",
    logbackClassic % "test"
  ),
  plugins = Seq(
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
