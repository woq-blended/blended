import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

println("Using Scala Build")

#include blended-build/build-common.scala

Model(
  blendedPersistenceOrient,
  packaging = "bundle",
  name = "${project.artifactId}",
  description = "Implement a persistence backend with OrientDB.",
  parent = blendedParent,
  dependencies = Seq(
    // compile
    blendedAkka,
    slf4j,
    domino,
    orientDbCore,
    // test
    scalaTest % "test",
    blendedTestSupport % "test",
    mockitoAll % "test"
  ),
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  build = Build(
    plugins = Seq(
      Plugin(
        "org.apache.felix" % "maven-bundle-plugin",
        extensions = true
      ),
      Plugin(
        "net.alchim31.maven" % "scala-maven-plugin"
      ),
      Plugin(
        "org.scalatest" % "scalatest-maven-plugin"
      )
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
