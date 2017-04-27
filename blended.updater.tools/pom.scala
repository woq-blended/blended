import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  blendedUpdaterTools,
  packaging = "bundle",
  description = "Configurations for Updater and Launcher",
  dependencies = Seq(
    typesafeConfig,
    blendedUpdaterConfig,
    cmdOption,
    scalaTest % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin,
    scoverageMavenPlugin
  )
)
