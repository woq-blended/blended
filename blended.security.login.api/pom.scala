import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.securityLoginApi,
  packaging = "bundle",
  description = "API to provide the backend  for a Login Service.",
  dependencies = Seq(
    scalaLib % "provided",
    Blended.domino,
    Blended.akka,
    Deps.prickle,
    Deps.jjwt,
    Blended.security,
    Blended.testSupport % "test",
    Blended.testSupportPojosr % "test",
    scalaTest % "test",
    logbackCore % "test",
    logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
