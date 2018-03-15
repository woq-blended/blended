import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  blendedJmsUtils,
  packaging = "bundle",
  description =
    """
      |A bundle to provide a ConnectionFactory wrapper that monitors a single connection and is able
      |to monitor the connection via an active ping.
    """.stripMargin,
  dependencies = Seq(
    scalaLib % "provided",
    blendedDomino,
    blendedMgmtBase,
    blendedContainerContext,
    camelJms,
    blendedAkka,
    jms11Spec,
    log4s,
    scalaTest % "test",
    akkaSlf4j % "test",
    mockitoAll % "test",
    activeMqBroker % "test",
    activeMqKahadbStore % "test",
    akkaTestkit % "test",
    blendedCamelUtils % "test",
    blendedTestSupport % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
