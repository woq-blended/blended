import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  blendedSpray,
  packaging = "bundle",
  description = "Define a Servlet bridge tapping into an OSGI HTTP service as one way to publish Spray based HTTP endpoints.",
  dependencies = Seq(
    akkaOsgi,
    blendedAkka,
    blendedSprayApi,
    scalaLib,
    geronimoServlet30Spec,
    orgOsgi
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin
  )
)
