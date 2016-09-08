import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-versions.scala
#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedUpdaterConfig,
  packaging = "bundle",
  description = "Configurations for Updater and Launcher",
  dependencies = Seq(
    scalaLib % "provided",
    typesafeConfig,
    slf4j,
    scalaTest % "test",
    blendedTestSupport % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin,
    prepareSbtPlugin,
    compileJsPlugin,
    Plugin(
      buildHelperPlugin,
      executions = Seq(
        Execution(
          id = "addSources",
          phase = "generate-sources",
          goals = Seq("add-source"),
          configuration = Config(
            sources = "src/shared/scala"
          )
        )
      )
    ),
    Plugin(
      gav = buildHelperPlugin,
      executions = Seq(
        Execution(
          id = "attachJS",
          phase = "package",
          goals = Seq("attach-artifact"),
          configuration = Config(
            artifacts = Config(
              artifact = Config (
                file = "target/scala-" + scalaVersion.binaryVersion + "/${project.artifactId}_sjs" + scalaJsBinVersion + "_" + scalaVersion.binaryVersion + "-${project.version}.jar",
                classifier = "sjs" + scalaJsBinVersion + "_" + scalaVersion.binaryVersion
              )
            )
          )
        )
      )
    )
  )
)
