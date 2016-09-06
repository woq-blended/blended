import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedMgmtRepo,
  packaging = "bundle",
  description = "File Artifact Repository",
  dependencies = Seq(
    scalaLib % "provided",
    blendedDomino,
    blendedUpdaterConfig,
    blendedMgmtBase,
    sprayJson,
    scalaTest % "test",
    blendedTestSupport % "test",
    lambdaTest % "test"
  ), 
  plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin,
      scalatestMavenPlugin
  )
)
