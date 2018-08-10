import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.jettyBoot,
  packaging = "bundle",
  description = "Bundle wrapping the original jetty boot bundle to dynamically provide SSL Context via OSGI services.",
  dependencies = Seq(
    scalaLib % "provided",
    scalaReflect % "provided",
    Blended.domino,
    Blended.utilLogging,
    jettyOsgiBoot,
    logbackCore % "test",
    logbackClassic % "test",
    scalaTest % "test",
    Blended.testSupport % "test"
  ),
  plugins = Seq(
    Plugin(
      mavenBundlePlugin.gav,
      extensions = true,
      inherited = true,
      configuration = Config(
        instructions = Config(
          _include = "osgi.bnd",
          `Embed-Dependency` = s"*;artifactId=${jettyOsgiBoot.artifactId}"
        )
      )
    ),
    scalaCompilerPlugin,
    scalatestMavenPlugin
  )
)
