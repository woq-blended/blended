import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedItestSupport,
  packaging = "jar",
  description = """Define an integration test API for collaborating blended container(s) using docker as a runtime 
    for the container(s) under test and an Akka based Camel framework to perform the integration tests 
    as pure blackbox tests. Container(s) may be prestarted and discovered (for execution speed) or 
    started by the integration test (for reproducability).""",
  dependencies = Seq(
    scalaLib % "provided",
    blendedAkka,
    blendedJolokia,
    blendedTestSupport,
    blendedJmsUtils,
    dockerJava,
    akkaCamel,
    typesafeConfig,
    junit,
    commonsExec,
    camelCore,
    camelJms,
    camelHttp,
    slf4j,
    geronimoJms11Spec,
    commonsCompress,
    akkaSlf4j % "test",
    mockitoAll % "test",
    activeMqBroker % "test",
    activeMqKahadbStore % "test",
    logbackCore % "test",
    logbackClassic % "test",
    jolokiaJvmAgent % "runtime"
  ),
  plugins = Seq(
    Plugin(
      gav = Plugins.dependency,
      executions = Seq(
        Execution(
          id = "extract-blended-container",
          phase = "process-resources",
          goals = Seq(
            "copy-dependencies"
          ),
          configuration = Config(
            includeClassifiers = "agent",
            outputDirectory = "${project.build.directory}/jolokia"
          )
        )
      )
    ),
    sbtCompilerPlugin,
    Plugin(
      scalatestMavenPlugin.gav,
      executions = Seq(
        scalatestExecution    
      ),
      configuration = new Config(
          scalatestConfiguration.elements ++ 
          Seq(
            "argLine" -> Some("-javaagent:${project.build.directory}/jolokia/jolokia-jvm-" + BlendedVersions.jolokiaVersion + "-agent.jar=port=7777,host=localhost")
          )
      )
    )
  )
)
