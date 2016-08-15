import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedSecurity,
  packaging = "bundle",
  description = "Configuration bundle for the Apache Shiro security framework.",
  dependencies = Seq(
    blendedSecurityBoot,
    apacheShiroCore,
    apacheShiroWeb,
    blendedAkka,
    scalaLib % "provided"
  ),
  plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin
  )
)
