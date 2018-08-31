import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.activemqClient,
  packaging = "bundle",
  description =
    """
      |An Active MQ Connection factory as a service.
    """.stripMargin,
  dependencies = Seq(
    Blended.domino,
    activeMqClient
  ),
  plugins = Seq(
    mavenBundlePlugin,
    scalaCompilerPlugin
  )
)
