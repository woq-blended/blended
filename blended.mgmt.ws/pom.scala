import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.mgmtWs,
  packaging = "bundle",
  description = "Web sockets interface for Mgmt clients.",
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  dependencies = Seq(
    scalaLib % "provided",
    slf4j % "provided",
    Blended.domino,
    Blended.akka,
    Blended.akkaHttp,
    Blended.updaterConfig,
    Blended.prickle,
    Blended.utilLogging,
    Blended.securityLoginApi,
    Deps.akkaHttp,
    Deps.akkaHttpCore,
    Deps.akkaStream,
    Deps.sttp % "test",
    sttpAkka % "test",
    Blended.testSupport % "test",
    Blended.testSupportPojosr % "test",
    Blended.securityLoginImpl % "test",
    Blended.securityLoginRest % "test",
    Blended.persistenceH2 % "test",
    Blended.mgmtRest % "test",
    Blended.updaterRemote % "test",
    Deps.scalaTest % "test",
    Deps.logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
