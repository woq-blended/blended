import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../../blended.build/build-versions.scala
#include ../../blended.build/build-dependencies.scala
#include ../../blended.build/build-plugins.scala
#include ../../blended.build/build-common.scala

BlendedDockerContainer(
  gav = blendedDockerDemoLauncher,
  image = Dependency(
    gav = blendedDemoLauncher,
    `type` = "tar.gz",
    classifier = "full-nojre",
    scope = "provided"
  ),
  folder = "launcher",
  ports = List(1099,1883,8080)
)
