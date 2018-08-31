import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.jolokia,
  packaging = "bundle",
  description = "Provide an Actor based Jolokia Client to access JMX resources of a container via REST.",
  dependencies = Seq(
    Blended.akka,
    Deps.scalaLib,
    Deps.sprayJson,
    Deps.jsonLenses,
    Deps.slf4j,
    Deps.akkaHttp,
    Deps.akkaStream,
    Deps.akkaSlf4j % "test",
    Deps.jolokiaJvmAgent % "runtime",
    Deps.scalaTest % "test",
    Blended.testSupport % "test",
    Deps.mockitoAll % "test",
    Deps.slf4jLog4j12 % "test"
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
    mavenBundlePlugin,
    scalaCompilerPlugin,
    Plugin(
      gav = Plugins.scalaTest,
      configuration = Config(
        argLine = "-javaagent:${project.build.directory}/jolokia/jolokia-jvm-${jolokia.version}-agent.jar=port=7777,host=localhost"
      )
    )
  )
)
