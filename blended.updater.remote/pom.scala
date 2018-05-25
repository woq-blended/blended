import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.updaterRemote,
  packaging = "bundle",
  description = "OSGi Updater remote handle support",
  dependencies = Seq(
    scalaLib % "provided",
    orgOsgi,
    domino,
    akkaOsgi,
    slf4j,
    Blended.persistence,
    typesafeConfig,
    Blended.updaterConfig,
    Blended.mgmtBase,
    Blended.launcher,
    Blended.containerContextApi,
    Blended.akka,
    Blended.sprayApi,
    akkaTestkit % "test",
    scalaTest % "test",
    felixFramework % "test",
    logbackClassic % "test",
    akkaSlf4j % "test",
    felixGogoRuntime % "test",
    felixGogoShell % "test",
    felixGogoCommand % "test",
    felixFileinstall % "test",
    mockitoAll % "test",
    Blended.testSupport % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
