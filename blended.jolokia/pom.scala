import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-common.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala

BlendedModel(
  blendedJolokia,
  packaging = "bundle",
  description = "Provide an Actor based Jolokia Client to access JMX resources of a container via REST.",
  dependencies = Seq(
    blendedAkka,
    scalaLib,
    sprayHttp,
    sprayHttpx,
    sprayUtil,
    sprayIo,
    sprayClient,
    sprayJson,
    jsonLenses,
    slf4j,
    akkaSlf4j % "test",
    Dependency(
      "org.jolokia" % "jolokia-jvm" % BlendedVersions.jolokiaVersion,
      classifier = "agent",
      scope = "runtime"
    ),
    scalaTest % "test",
    blendedTestSupport % "test",
    mockitoAll % "test",
    slf4jLog4j12 % "test"
  ),
  plugins = Seq(
    Plugin(
        gav = mavenDependencyPlugin,
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
    scalaMavenPlugin,
    Plugin(
    		scalatestMavenPlugin.gav,
        configuration = Config(
          argLine = "-javaagent:${project.build.directory}/jolokia/jolokia-jvm-${jolokia.version}-agent.jar=port=7777,host=localhost"
        )
      )
  )
)
