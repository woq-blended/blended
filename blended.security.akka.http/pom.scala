import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedSecurityAkkaHttp,
  packaging = "bundle",
  description = "Some security aware Akka HTTP routes for the blended container.",
  dependencies = Seq(
		Deps.scalaLib,
    blendedAkka,
    Deps.akkaHttp,
    Deps.orgOsgi,
    Deps.orgOsgiCompendium,
    Deps.slf4j,
    Deps.apacheShiroCore
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin
  )
)
