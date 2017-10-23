import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

val itestModules = Seq(
  "blended.itest.node"
)

BlendedModel(
  gav = BlendedModule("blended.itest.reactor"),
  packaging = "pom",
  description = "The integration tests for the demo blended containers.",
  plugins = Seq(
    skipInstallPlugin,
    skipDeployPlugin
  ),
  profiles = Seq(
    Profile(
      id = "itest",
      activation = Activation(
        activeByDefault = false
      ),
      modules = itestModules
    ),
    Profile(
      id = "all",
      modules = itestModules
    )
  )
)
