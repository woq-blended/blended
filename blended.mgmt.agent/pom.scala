import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.mgmtAgent,
  packaging = "bundle",
  description = "Bundle to regularly report monitoring information to a central container hosting the container registry.",
  dependencies = Seq(
    scalaLib % "provided",
    Blended.akka,
    Blended.updaterConfig,
    Blended.sprayApi,
    Blended.spray,
    orgOsgi,
    akkaOsgi,
    akkaTestkit % "test",
    scalaTest % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin
  )
)
