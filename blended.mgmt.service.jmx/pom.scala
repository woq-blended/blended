import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.mgmtServiceJmx,
  packaging = "bundle",
  description = "A JMX based Service Info Collector.",
  dependencies = Seq(
    scalaLib % "provided",
    Blended.domino,
    Blended.akka,
    Blended.updaterConfig,
    akkaTestkit % "test",
    scalaTest % "test",
    slf4jLog4j12 % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)