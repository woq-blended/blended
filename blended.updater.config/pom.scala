import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

val jsArtifact = blendedUpdaterConfig.groupId.get %%% blendedUpdaterConfig.artifactId % blendedUpdaterConfig.version.get

/**
 * Sources under "shared" dir are for scala-jvm and scala-js 
 * Sources under "src" dir are only for scala-jvm
 */
BlendedModel(
  gav = blendedUpdaterConfig,
  packaging = "bundle",
  description = "Configurations for Updater and Launcher",
  dependencies = Seq(
    scalaLib % "provided",
    typesafeConfig,
    slf4j,
    prickle,
    scalaTest % "test",
    blendedTestSupport % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    Plugin(
      gav = Plugins.scala,
      executions = Seq(
        scalaExecution_prepareSbt
      )
    ),
    scalatestMavenPlugin,
    Plugin(
      gav = Plugins.exec,
      executions = Seq(
        execExecution_compileJs(execId = "compileJS", phase = "compile", args = List("-batch", "fastOptJS", "test")),
        execExecution_compileJs(execId = "packageJS", phase = "package", args = List("-batch", "packageBin"))
      )
    ),
    Plugin(
      gav = Plugins.buildHelper,
      executions = Seq(
        Execution(
          id = "addSources",
          phase = "generate-sources",
          goals = Seq("add-source"),
          configuration = Config(
            sources = Config(
              source =  "shared/main/scala"
            )
          )
        ),
        Execution(
          id = "addTestSources",
          phase = "generate-sources",
          goals = Seq("add-test-source"),
          configuration = Config(
            sources = Config(
              source =  "shared/test/scala"
            )
          )
        )
      )
    ),
    Plugin(
      gav = Plugins.install,
      executions = Seq(
        Execution(
          id = "publishJS",
          phase = "install",
          goals = Seq("install-file"),
          configuration = Config(
            file = "target/scala-" + scalaVersion.binaryVersion + "/" + jsArtifact.artifactId + "-" + BlendedVersions.blendedVersion + ".jar",
            groupId = jsArtifact.groupId.get,
            artifactId = jsArtifact.artifactId,
            version = jsArtifact.version.get,
            packaging = "jar"
          )
        )
      )
    )
  )
)
