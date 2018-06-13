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
    Deps.scalaLib % "provided",
    Deps.orgOsgi,
    Deps.domino,
    Deps.akkaOsgi,
    Deps.slf4j,
    Blended.persistence,
    Deps.typesafeConfig,
    Blended.updaterConfig,
    Blended.mgmtBase,
    Blended.launcher,
    Blended.containerContextApi,
    Blended.akka,
    Blended.sprayApi,
    Deps.akkaTestkit % "test",
    Deps.scalaTest % "test",
    Deps.felixFramework % "test",
    Deps.logbackClassic % "test",
    Deps.akkaSlf4j % "test",
    Deps.felixGogoRuntime % "test",
    Deps.felixGogoShell % "test",
    Deps.felixGogoCommand % "test",
    Deps.felixFileinstall % "test",
    Deps.mockitoAll % "test",
    Blended.testSupport % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
