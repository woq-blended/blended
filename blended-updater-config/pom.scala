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
      gav = scalaMavenPlugin.gav,
      executions = Seq(
        Execution(
          id = "prepareSBT",
          phase = "generate-resources",
          goals = Seq(
            "script"
          ),
          configuration = Config(
            script = scriptHelper +
              """
import java.io.File

ScriptHelper.writeFile(
  new File(project.getBasedir(), "project/build.properties"),
  "sbtVersion=""" + Versions.sbtVersion + """"
)

ScriptHelper.writeFile(
  new File(project.getBasedir(), "project/plugins.sbt"),
  "resolvers += \"Typesafe repository\" at \"http://repo.typesafe.com/typesafe/releases/\"\n" +
  "\n" +
  "addSbtPlugin(\"org.scala-js\" % \"sbt-scalajs\" % \"""" + Versions.scalaJsVersion + """\")"
)
"""
          )
        )
      )
    )
  )
)
