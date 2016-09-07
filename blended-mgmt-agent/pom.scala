import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-versions.scala
#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  blendedMgmtAgent,
  packaging = "bundle",
  description = "Bundle to regularly report monitoring information to a central container hosting the container registry.",
  dependencies = Seq(
    scalaLib % "provided",
    blendedAkka,
    blendedContainerRegistry,
    blendedUpdaterConfig,
    blendedSprayApi,
    orgOsgi,
    akkaOsgi,
    akkaTestkit % "test",
    scalaTest % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin
  )
)
