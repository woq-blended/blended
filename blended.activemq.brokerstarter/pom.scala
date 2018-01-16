import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedActivemqBrokerstarter,
  packaging = "bundle",
  description =
    """
      |A simple wrapper around an Active MQ broker that makes sure that the broker
      |is completely started before exposing a connection factory OSGi service.
    """.stripMargin,
  dependencies = Seq(
    blendedAkka,
    blendedJmsUtils,
    camelJms,
    activeMqBroker,
    activeMqSpring,
    scalaLib % "provided"
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin
  )
)
