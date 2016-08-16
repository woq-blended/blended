import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedContainerRegistry,
  packaging = "bundle",
  description = """The container registry serves as the centerpiece for collecting container information. Individual containers 
    regularly post their monitoring information associated with their UUID and the container registry will keep 
    track of that information relying on the persistence services defined in other bundles.""",
  dependencies = Seq(
    scalaLib % "provided",
    blendedPersistence,
    blendedAkka,
    blendedMgmtBase,
    blendedUpdaterConfig,
    blendedUpdaterRemote,
    akkaActor,
    sprayJson,
    slf4j,
    akkaSlf4j % "test",
    scalaTest % "test",
    mockitoAll % "test",
    blendedTestSupport % "test",
    slf4jLog4j12 % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin
  )
)
