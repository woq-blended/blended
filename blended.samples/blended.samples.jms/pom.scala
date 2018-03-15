import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../../blended.build/build-versions.scala
//#include ../../blended.build/build-dependencies.scala
//#include ../../blended.build/build-plugins.scala
//#include ../../blended.build/build-common.scala

BlendedModel(
  gav = blendedSamplesJms,
  packaging = "bundle",
  description = "A combined JMS example.",
  dependencies = Seq(
    blendedDomino,
    blendedCamelUtils,
    domino,
    camelCore,
    camelJms,
    geronimoJms11Spec,
    slf4j
  ),
  plugins = Seq(
    mavenBundlePlugin,
    sbtCompilerPlugin
  )
)
