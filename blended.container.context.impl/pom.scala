import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedContainerContextImpl,
  packaging = "bundle",
  description = "A simple OSGI service to provide access to the container's config directory.",
  dependencies = Seq(
    scalaLib % "provided",
    blendedContainerContextApi,
    blendedUtil,
    blendedUpdaterConfig,
    blendedLauncher,
    orgOsgiCompendium,
    orgOsgi,
    domino,
    slf4j,
    log4s,
    julToSlf4j,
    scalaTest % "test",
    mockitoAll % "test",
    logbackCore % "test",
    logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
