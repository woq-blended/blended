import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include blended-build/build-common.scala

Model(
  blendedJmx,
  packaging = "bundle",
  name = "${project.artifactId}",
  description = "Helper bundle to expose the platform's MBeanServer as OSGI Service.",
  parent = blendedParent,
  dependencies = Seq(
    domino
  ),
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  build = Build(
    plugins = Seq(
      mavenBundlePlugin,
      scalaMavenPlugin
    )
  ),
  profiles = Seq(Profile(
    id = "gen-pom",
    build = Build(
      plugins = Seq(
        generatePomXml(phase = "validate")
      )
    )
  )),
  modelVersion = "4.0.0"
)
