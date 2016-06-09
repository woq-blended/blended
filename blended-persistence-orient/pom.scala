import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

println("Using Scala Build")

#include blended-build/build-common.scala

Model(
  blendedPersistenceOrient,
  packaging = "bundle",
  name = "${project.artifactId}",
  description = "Implement a persistence backend with OrientDB.",
  parent = Parent(
    gav = blendedGroupId % "blended.parent" % "2.0-SNAPSHOT",
    relativePath = "../blended-parent"
  ),
  dependencies = Seq(
    // compile
    blendedAkka,
    slf4j,
    domino,
    orientDbCore,
    // test
    scalaTest % "test",
    blendedTestSupport % "test",
    mockito % "test"
  ),
  properties = Map(
    "bundle.symbolicName" -> "${project.artifactId}",
    "bundle.namespace" -> "${project.artifactId}"
  ),
  build = Build(
    plugins = Seq(
      // Generate pom.xml
      Plugin(
        "io.takari.polyglot" % "polyglot-translate-plugin" % "0.1.15",
        executions = Seq(
          Execution(
            id = "generate-pom.xml",
            goals = Seq("translate"),
            phase = "none",
            configuration = Config(
              input = "pom.scala",
              output = "pom.xml"
            )
          )
        )
      ),
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
        // Generate pom.xml
        Plugin(
          "io.takari.polyglot" % "polyglot-translate-plugin" % "0.1.15",
          executions = Seq(
            Execution(
              id = "generate-pom.xml",
              goals = Seq("translate"),
              phase = "validate",
              configuration = Config(
                input = "pom.scala",
                output = "pom.xml"
              )
            )
          )
        )
      )
    )
  )),
  modelVersion = "4.0.0"
)
