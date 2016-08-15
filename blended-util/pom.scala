import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  blendedUtil,
  packaging = "bundle",
  description = "Utility classes to use in other bundles.",
  dependencies = Seq(
    akkaActor,
    orgOsgi,
    orgOsgiCompendium,
    slf4j,
    "org.slf4j" % "slf4j-log4j12" % slf4jVersion % "test",
    scalaTest % "test",
    akkaTestkit % "test",
    junit % "test"
  ),
  build = Build(
    plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin,
      scalatestMavenPlugin
    )
  )
)
