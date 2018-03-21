import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedUpdater,
  packaging = "bundle",
  description = "OSGi Updater",
  dependencies = Seq(
    scalaLib % "provided",
    orgOsgi,
    domino,
    akkaOsgi,
    slf4j,
    typesafeConfig,
    blendedUpdaterConfig,
    blendedLauncher,
    blendedMgmtBase,
    blendedContainerContextApi,
    blendedAkka,
    blendedSprayApi,
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
    blendedTestSupport % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
