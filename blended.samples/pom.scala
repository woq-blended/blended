import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-common.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala

BlendedModel(
  gav = blendedSamplesReactor,
  packaging = "pom",
  profiles = Seq(
    Profile(
      id = "build",
      modules = Seq(
        "blended.samples.camel",
        "blended-samples-jms",
        "blended.samples.spray.helloworld"
      )
    )
  )
)
