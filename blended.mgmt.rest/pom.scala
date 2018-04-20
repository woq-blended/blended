import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  blendedMgmtRest,
  packaging = "bundle",
  description = "REST interface to accept POST's from distributed containers. These will be delegated to the container registry.",
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  dependencies = Seq(
    scalaLib % "provided",
    slf4j % "provided",
    blendedMgmtBase,
    blendedAkka,
    Deps.akkaHttp,
    Deps.akkaHttpCore,
    Deps.akkaStream,
    blendedAkkaHttp,
    blendedPrickleAkkaHttp,
    blendedSecurityAkkaHttp,
    blendedUpdaterRemote,
    orgOsgi,
    orgOsgiCompendium,
    scalaTest % "test",
    Deps.akkaHttpTestkit % "test",
    mockitoAll % "test",
    Deps.logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
