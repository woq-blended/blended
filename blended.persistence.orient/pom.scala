import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.persistenceOrient,
  packaging = "bundle",
  description = "Implement a persistence backend with OrientDB.",
  dependencies = Seq(
    scalaLib % "provided",
    Blended.persistence,
    Blended.akka,
    slf4j,
    domino,
    orientDbCore,
    scalaTest % "test",
    Dependency(
      Blended.testSupport,
      scope = "test",
      exclusions = Seq("*" % "*")
    ),
    mockitoAll % "test",
    logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
