import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.securityTest,
  packaging = "jar",
  description = "Test cases for blended.security",
  dependencies = Seq(
    Blended.security % "test",
    Blended.testSupport % "test",
    Blended.testSupportPojosr % "test",
    scalaLib % "provided",
    scalaTest % "test",
    logbackCore % "test",
    logbackClassic % "test"
  ),
  plugins = Seq(
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
