import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../../blended.build/build-versions.scala
#include ../../blended.build/build-dependencies.scala
#include ../../blended.build/build-plugins.scala
#include ../../blended.build/build-common.scala

BlendedModel(
  gav = blendedSamplesCamel,
  packaging = "bundle",
  description = "A sample camel route.",
  dependencies = Seq(
    blendedCamelUtils,
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
