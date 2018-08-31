import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

val jsArtifact = Blended.security.groupId.get % (Blended.security.artifactId + "_sjs0.6_" + BlendedVersions.scalaVersionJSBinary) % Blended.security.version.get

BlendedModel(
  gav = Blended.security,
  packaging = "bundle",
  description = "Configuration bundle for the security framework.",
  dependencies = Seq(
    Blended.securityBoot,
    Blended.akka,
    Blended.util,
    Blended.domino,
    Blended.utilLogging,
    Deps.prickle,
    Blended.testSupport % "test",
    scalaLib % "provided",
    scalaTest % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
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
        execExecution_compileJs(execId = "sbt-compileJS", phase = "compile", args = List("-batch", "fastOptJS", "test")),
        execExecution_compileJs(execId = "sbt-packageJS", phase = "package", args = List("-batch", "packageBin"))
      )
    ),
    Plugin(
      gav = Plugins.buildHelper,
      executions = Seq(
        Execution(
          id = "addSources-shared",
          phase = "initialize",
          goals = Seq("add-source"),
          configuration = Config(
            sources = Config(
              source =  "shared/main/scala"
            )
          )
        ),
        Execution(
          id = "addTestSources-shared",
          phase = "initialize",
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
            file = "target/scala-" + BlendedVersions.scalaVersionJSBinary + "/" + jsArtifact.artifactId + "-" + BlendedVersions.blendedVersion + ".jar",
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
