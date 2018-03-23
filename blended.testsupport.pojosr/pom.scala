import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedTestSupportPojosr,
  packaging = "jar",
  description = "A simple Pojo based test container that can be used in unit testing",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.felixConnect,
    blendedContainerContextImpl,
    blendedDomino
  ),
  plugins = Seq(
    sbtCompilerPlugin
  )
)
