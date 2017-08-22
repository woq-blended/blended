import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedDemoReactor,
  packaging = "pom",
  description = "A collection of container definitions to demonstrate the functionality of blended.",
  modules = Seq(
    "blended.demo.mgmt/blended.demo.mgmt.resources",
    "blended.demo.mgmt",
    "blended.demo.node/blended.demo.node.resources",
    "blended.demo.node"
  )
)
