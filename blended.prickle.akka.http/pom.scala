import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.prickleAkkaHttp,
  packaging = "bundle",
  description = "Define some convenience to use Prickle with Akka HTTP",
  dependencies = Seq(
    Deps.log4s,
    Deps.akkaHttpCore,
    Deps.akkaHttp,
    Deps.akkaStream,
    Deps.prickle,
    Deps.scalaTest % "test",
    Deps.akkaHttpTestkit % "test",
    Deps.logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
