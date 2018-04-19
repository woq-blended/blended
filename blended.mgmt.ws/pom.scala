import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  blendedMgmtWs,
  packaging = "bundle",
  description = "Web sockets interface for Mgmt clients.",
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  dependencies = Seq(
    scalaLib % "provided",
    slf4j % "provided",
    Deps.akkaHttp,
    Deps.akkaHttpCore,
    Deps.akkaStream,
    Deps.scalaTest % "test",
    Deps.akkaHttpTestkit % "test",
    Deps.logbackClassic % "test"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
