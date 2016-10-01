import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  blendedUtil,
  packaging = "bundle",
  description = "Utility classes to use in other bundles.",
  properties = Map(
    "loglevel.test" -> "INFO"
  ),
  dependencies = Seq(
    akkaActor,
    orgOsgi,
    orgOsgiCompendium,
    slf4j,
    junit % "test",
    slf4jLog4j12 % "test",
    scalaTest % "test",
    akkaTestkit % "test"
  ),
  dependencyManagement = DependencyManagement(
    Seq(
      scalaLib
    )
  ),
  plugins = BlendedModel.defaultPlugins ++ Seq(
      mavenBundlePlugin,
      scalaMavenPlugin,
      scalatestMavenPlugin
    )
)
