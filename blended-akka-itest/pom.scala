import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  blendedAkkaItest,
  packaging = "jar",
  description = "A sample integration test what uses docker to fire up the container(s) under test, execute the test suite and shutdown the container(s) afterwards.",
  dependencies = Seq(
    scalaLib,
    blendedDockerLauncherDemo % "provided",
    activeMqClient % "test",
    blendedItestSupport % "test",
    scalaTest % "test",
    slf4j % "test",
    akkaSlf4j % "test"
  ),
  plugins = Seq(
    scalaMavenPlugin,
    Plugin(
      scalatestMavenPlugin.gav,
      configuration = Config(
        argLine = "-Djava.net.preferIPv4Stack=true"
      )
    )
  )
)
