import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.spray,
  packaging = "bundle",
  description = "Define a Servlet bridge tapping into an OSGI HTTP service as one way to publish Spray based HTTP endpoints.",
  dependencies = Seq(
    akkaOsgi,
    Blended.akka,
    Blended.sprayApi,
    scalaLib,
    geronimoServlet30Spec,
    orgOsgi,
    prickle
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin
  )
)
