import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

val mavenVersion = "3.0.5"

BlendedModel(
  blendedUpdaterMavenPlugin,
  packaging = "maven-plugin",
  description = "Integration of Blended Updater feature / product builds into Maven",
  prerequisites = Prerequisites(
    maven = "${maven.version}"
  ),
  dependencies = Seq(
    "org.apache.maven" % "maven-plugin-api" % mavenVersion,
    "org.apache.maven.plugin-tools" % "maven-plugin-annotations" % "3.4" % "provided",
    "org.apache.maven" % "maven-core" % mavenVersion,
    blendedUpdaterTools,
    scalaLib
  ),
  plugins = Seq(
    scalaMavenPlugin,
    Plugin(
      gav = Plugins.plugin,
      executions = Seq(
        Execution(
          id = "default-descriptor",
          phase = "process-classes",
          goals = Seq(
            "descriptor"
          )
        ),
        Execution(
          id = "help-goal",
          goals = Seq(
            "helpmojo"
          ),
          configuration = Config(
            skipErrorNoDescriptorsFound = "true"
          )
        )
      ),
      configuration = Config(
        goalPrefix = "blended-updater"
      )
    )
  )
)
