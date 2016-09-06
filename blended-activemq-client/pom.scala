import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  blendedActivemqClient,
  packaging = "bundle",
  description =
    """
      |An Active MQ Connection factory as a service.
    """.stripMargin,
  dependencies = Seq(
    blendedDomino,
    activeMqClient
  ),
  build = Build(
    plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin
    )
  )
)
