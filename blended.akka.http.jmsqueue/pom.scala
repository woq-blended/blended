import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.akkaHttpJmsQueue,
  packaging = "bundle",
  description = "Provide a simple REST interface to consume messages from JMS Queues",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.scalaReflect % "provided",
    Deps.domino,
    Blended.domino,
    Blended.containerContextApi,
    Blended.akka,
    Blended.akkaHttp,
    Blended.util,
    Deps.jms11Spec,
    Deps.akkaSlf4j % "test",
    Blended.testSupportPojosr % "test",
    Deps.akkaTestkit % "test",
    Deps.akkaSlf4j % "test",
    //    Deps.mockitoAll % "test",
    Deps.scalaTest % "test",
    Deps.akkaHttpTestkit % "test",
    Deps.logbackCore % "test",
    Deps.logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
