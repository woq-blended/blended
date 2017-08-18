import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../../blended.build/build-versions.scala
#include ../../blended.build/build-dependencies.scala
#include ../../blended.build/build-plugins.scala
#include ../../blended.build/build-common.scala

BlendedModel(
  gav = blendedSamplesSprayHelloworld,
  packaging = "war",
  description = "A sample Spray based HTTP endpoint for the blended container.",
  dependencies = Seq(
    blendedSprayApi,
    blendedSpray,
    blendedAkka,
    scalaLib,
    orgOsgi,
    orgOsgiCompendium,
    slf4j,
    geronimoServlet30Spec,
    apacheShiroCore,
    blendedSecuritySpray,
    scalaTest % "test",
    sprayTestkit % "test",
    mockitoAll % "test",
    slf4jLog4j12 % "test"
  ),
  plugins = Seq(
    bundleWarPlugin,
    scalaMavenPlugin,
    scalatestMavenPlugin,
    Plugin(
      gav = Plugins.war,
      configuration = Config (
        packagingExcludes = "WEB-INF/lib/*.jar",
        archive = Config(
          manifestFile = "${project.build.outputDirectory}/META-INF/MANIFEST.MF"
        )
      )
    )
  )
)
