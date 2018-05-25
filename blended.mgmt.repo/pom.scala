import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.mgmtRepo,
  packaging = "bundle",
  description = "File Artifact Repository",
  dependencies = Seq(
    scalaLib % "provided",
    Blended.domino,
    Blended.updaterConfig,
    Blended.mgmtBase,
    sprayJson,
    scalaTest % "test",
    Blended.testSupport % "test",
    lambdaTest % "test"
  ), 
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
