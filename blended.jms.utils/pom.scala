import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.jmsUtils,
  packaging = "bundle",
  description =
    """
      |A bundle to provide a ConnectionFactory wrapper that monitors a single connection and is able
      |to monitor the connection via an active ping.
    """.stripMargin,
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Blended.domino,
    Blended.mgmtBase,
    Blended.containerContextApi,
    Blended.updaterConfig,
    Deps.camelJms,
    Blended.akka,
    Deps.jms11Spec,
    Deps.log4s,
    Deps.scalaTest % "test",
    Deps.akkaSlf4j % "test",
    Deps.mockitoAll % "test",
    Deps.activeMqBroker % "test",
    Deps.activeMqKahadbStore % "test",
    Deps.akkaTestkit % "test",
    Blended.camelUtils % "test",
    Blended.testSupport % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    // FIXME: use scalaCompilerPlugin instead, but it currently has test compile errors when used (TR)
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
