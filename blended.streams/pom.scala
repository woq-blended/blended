import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedStreams,
  packaging = "bundle",
  description = "A bundle with functionality to work with akka streams",
  dependencies = Seq(
    scalaLib % "provided",
    scalaReflect % "provided",
    akkaStream,
    blendedJmsUtils,
    blendedTestSupport % "test",
    activeMqBroker % "test",
    activeMqKahadbStore % "test",
    akkaTestkit % "test",
    akkaSlf4j % "test",
    mockitoAll % "test",
    logbackCore % "test",
    logbackClassic % "test",
    scalaTest % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin
  )
)
