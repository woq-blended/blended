import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedMgmtRepoRest,
  packaging = "war",
  description = "File Artifact Repository REST Service",
  dependencies = Seq(
    scalaLib % "provided",
    blendedDomino,
    blendedUpdaterConfig,
    blendedMgmtBase,
    blendedMgmtRepo,
    blendedSpray,
    blendedSecuritySpray,
    sprayJson,
    scalaTest % "test"
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
