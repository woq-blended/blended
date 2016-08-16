import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedLauncher,
  packaging = "bundle",
  description = "Provide an OSGi Launcher",
  dependencies = Seq(
    scalaLib % "provided",
    orgOsgi,
    slf4j,
    typesafeConfig,
    commonsDaemon,
    blendedUpdaterConfig,
    cmdOption,
    logbackClassic % "runtime",
    scalaTest % "test",
    felixFramework % "test",
    felixGogoRuntime % "test",
    felixGogoShell % "test",
    felixGogoCommand % "test",
    felixFileinstall % "test",
    felixMetatype % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin,
    Plugin(
      "org.apache.maven.plugins" % "maven-assembly-plugin",
      executions = Seq(
        Execution(
          id = "bin",
          phase = "package",
          goals = Seq(
            "single"
          )
        )
      ),
      configuration = Config(
        descriptors = Config(
          descriptor = "src/main/assembly/bin.xml"
        )
      )
    )
  )
)
