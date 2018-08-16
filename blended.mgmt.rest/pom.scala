import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.mgmtRest,
  packaging = "bundle",
  description = "REST interface to accept POST's from distributed containers. These will be delegated to the container registry.",
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.slf4j,
    Blended.utilLogging,
    Blended.mgmtBase,
    Blended.mgmtRepo,
    Blended.akka,
    Deps.akkaHttp,
    Deps.akkaHttpCore,
    Deps.akkaStream,
    Blended.akkaHttp,
    Blended.prickleAkkaHttp,
    Blended.securityAkkaHttp,
    Blended.updaterRemote,
    Deps.orgOsgi,
    Deps.orgOsgiCompendium,
    Deps.scalaTest % "test",
    Deps.akkaHttpTestkit % "test",
    Deps.mockitoAll % "test",
    Deps.logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
