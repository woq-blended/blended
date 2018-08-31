import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.utilLogging,
  packaging = "bundle",
  description = "Logging utility classes to use in other bundles.",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    orgOsgi,
    orgOsgiCompendium,
    slf4j,
    scalaXml,
    junit % "test",
    logbackCore % "test",
    logbackClassic % "test",
    scalaTest % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
