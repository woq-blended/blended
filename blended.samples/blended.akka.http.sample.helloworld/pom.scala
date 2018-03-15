import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../../blended.build/build-versions.scala
//#include ../../blended.build/build-dependencies.scala
//#include ../../blended.build/build-plugins.scala
//#include ../../blended.build/build-common.scala

BlendedModel(
  gav = blendedAkkaHttpSampleHelloworld,
  packaging = "bundle",
  description = "A sample Akka HTTP bases HTTP endpoint for the blended container.",
  dependencies = Seq(
    blendedAkka,
    blendedAkkaHttp,
    Deps.domino,
    Deps.scalaLib,
    Deps.orgOsgi,
    Deps.orgOsgiCompendium,
    Deps.slf4j,
    scalaTest % "test",
    slf4jLog4j12 % "test",
    Deps.akkaHttpTestkit % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
