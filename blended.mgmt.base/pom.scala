import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.mgmtBase,
  packaging = "bundle",
  description = "Shared classes for management and reporting facility.",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Blended.domino,
    Blended.containerContextApi,
    Blended.util,
    Deps.log4s,
    Deps.scalaTest % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
