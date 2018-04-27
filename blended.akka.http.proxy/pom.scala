import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.akkaHttpProxy,
  packaging = "bundle",
  description = "Provide Akka HTTP Proxy support",
  properties = Map(
    "logback.debug" -> "true"
  ),
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.scalaReflect % "provided",
    Deps.domino,
    Blended.domino,
    Blended.containerContextApi,
    //    Blended.util,
    //    Deps.orgOsgi,
    //    Deps.akkaOsgi,
    Blended.akka,
    Blended.akkaHttp,
    Blended.util,
    Deps.akkaStream,
    Deps.akkaHttp,
    Deps.log4s,
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
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
