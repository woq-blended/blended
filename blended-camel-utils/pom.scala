import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended-build/build-versions.scala
#include ../blended-build/build-common.scala
#include ../blended-build/build-dependencies.scala
#include ../blended-build/build-plugins.scala

BlendedModel(
  gav = blendedCamelUtils,
  packaging = "bundle",
  description = """A bundle to create a more OSGI-like solution when using the Camel Servlet Component on top 
    of an HTTP OSGI Service. Also see http://www.wayofquality.de/open%20source/camel/using-camel-servlets-within-osgi/""",
  dependencies = Seq(
    scalaLib % "provided",
    geronimoServlet25Spec,
    orgOsgi,
    orgOsgiCompendium,
    camelServlet,
    camelJms,
    slf4j,
    blendedAkka
  ),
  plugins = Seq(
    scalaMavenPlugin,
    mavenBundlePlugin
  )
)
