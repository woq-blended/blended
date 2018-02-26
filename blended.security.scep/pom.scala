import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedSecurityScep,
  packaging = "bundle",
  description = "Bundle to manage the container certificate via SCEP.",
  dependencies = Seq(
    scalaLib % "provided",
    scalaReflect % "provided",
    blendedDomino,
    commonsCodec,
    blendedSecuritySsl,
    bouncyCastleBcprov,
    bouncyCastlePkix,
    blendedUtil,
    slf4j,
    jcip,
    scep,
    logbackCore % "test",
    logbackClassic % "test",
    scalaTest % "test"
  ),
  plugins = Seq(
    Plugin(
      mavenBundlePlugin.gav,
      extensions = true,
      inherited = true,
      configuration = Config(
        instructions = new Config(Seq(
          "_include" -> Option("osgi.bnd"),
          "Embed-Dependency" -> Option(s"*;artifactId=${commonsCodec.artifactId},artifactId=${jcip.artifactId},${scep.artifactId},${bouncyCastleBcprov.artifactId},${bouncyCastlePkix.artifactId}")
        ))
      )
    ),
    sbtCompilerPlugin,
    scalatestMavenPlugin
  )
)
