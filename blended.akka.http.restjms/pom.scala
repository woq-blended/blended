import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.akkaHttpRestJms,
  packaging = "bundle",
  description = "Provide a simple REST interface to perform JMS request / reply operations",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.scalaReflect % "provided",
    Deps.domino,
    Deps.camelCore,
    Deps.camelJms,
    Blended.camelUtils,
    Blended.domino,
    Blended.containerContextApi,
    Deps.akkaStream,
    Blended.akka,
    Blended.akkaHttp,
    Blended.util,
    Deps.jms11Spec,
    Deps.activeMqBroker % "test",
    Deps.activeMqClient % "test",
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
