import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  blendedJmsUtils,
  packaging = "bundle",
  description =
    """
      |A bundle to privide a ConnectionFactory wrapper that monitors a single connection and is able
      |to monitor the connection via an active ping.
    """.stripMargin,
  dependencies = Seq(
    blendedDomino,
    blendedMgmtBase,
    scalaLib,
    camelJms,
    blendedAkka,
    jms11Spec
  ),
  build = Build(
    plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin
    )
  )
)
