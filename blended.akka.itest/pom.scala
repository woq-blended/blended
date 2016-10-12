import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

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
      gav = scalatestMavenPlugin.gav,
      executions = Seq(
        Execution(
          id = "test",
          goals = Seq("test")
        )
      ),
      configuration = Config(
        reportsDirectory = "${project.build.directory}/surefire-reports",
        junitxml = ".",
        stdout = "FT"
      )
    )
  )
)
