import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-versions.scala
#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedSecurityBoot,
  packaging = "bundle",
  description = "A Shiro Login Module for the blended Container.",
  dependencies = Seq(
    orgOsgi,
    scalaLib % "provided"
  ),
  plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin
  )
)
