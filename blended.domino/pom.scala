import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  Blended.domino,
  packaging = "bundle",
  description = "Blended Domino extension for new Capsule scopes.",
  dependencies = Seq(
    domino,
    typesafeConfig,
    Blended.containerContextApi,
    scalaLib
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin
  )
)
