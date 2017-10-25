import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

val dockerModules = Seq(
        "blended.docker.demo.mgmt",
        "blended.docker.demo.node"
      )

BlendedModel(
  gav = blendedDockerReactor,
  packaging = "pom",
  description = "A collection of docker definitions to demonstrate how docker can be used for blended containers.",
  profiles = Seq(
    Profile(
      id = "docker",
      modules = dockerModules
    ),
    Profile(
      id = "all",
      modules = dockerModules
    )
  )
)
