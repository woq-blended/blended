import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala

BlendedModel(
  blendedMgmtMock,
  packaging = "jar",
  description = "Mock server to simulate a larger network of blended containers for UI testing.",
  dependencies = Seq(
    blendedMgmtBase,
    scalaLib,
    slf4j,
    slf4jLog4j12,
    upickle,
    wiremockStandalone
  ),
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  plugins = Seq(
    scalaMavenPlugin,
    Plugin(
      execMavenPlugin,
      configuration = Config(
        mainClass = "blended.mgmt.mock.MgmtMockServer"
      )
    )
  )
)
