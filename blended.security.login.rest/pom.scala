import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.securityLoginRest,
  packaging = "bundle",
  description = "A REST service providing login services and web token management",
  dependencies = Seq(
    scalaLib % "provided",
    Blended.domino,
    Blended.akkaHttp,
    Blended.securityBoot,
    Blended.security,
    Blended.securityAkkaHttp,
    Blended.akka % "test",
    Blended.securityLogin % "test",
    Blended.testSupport % "test",
    Blended.testSupportPojosr % "test",
    scalaTest % "test",
    akkaHttpTestkit % "test",
    sttp % "test",
    sttpAkka % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
