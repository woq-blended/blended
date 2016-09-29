import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../../blended.build/build-versions.scala
#include ../../blended.build/build-common.scala
#include ../../blended.build/build-dependencies.scala
#include ../../blended.build/build-plugins.scala

BlendedModel(
  gav = blendedSamplesJms,
  packaging = "bundle",
  description = "A combined JMS example.",
  dependencies = Seq(
    blendedDomino,
    domino,
    camelCore,
    camelJms,
    geronimoJms11Spec,
    slf4j
  ),
  build = Build(
    plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin
    )
  )
)
