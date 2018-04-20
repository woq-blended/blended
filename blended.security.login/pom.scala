import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.securityLogin,
  packaging = "bundle",
  description = "OSGI Services to support a token based authentication and authorization.",
  dependencies = Seq(
    scalaLib % "provided",
    Blended.domino,
    jjwt,
    slf4jLog4j12 % "test",
    scalaTest % "test"
  ), 
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
