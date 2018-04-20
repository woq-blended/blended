import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../../blended.build/build-versions.scala
//#include ../../blended.build/build-dependencies.scala
//#include ../../blended.build/build-plugins.scala
//#include ../../blended.build/build-common.scala

BlendedModel(
  gav = Blended.samplesSprayHelloworld,
  packaging = "war",
  description = "A sample Spray based HTTP endpoint for the blended container.",
  dependencies = Seq(
    Blended.sprayApi,
    Blended.spray,
    Blended.akka,
    scalaLib,
    orgOsgi,
    orgOsgiCompendium,
    slf4j,
    geronimoServlet30Spec,
    scalaTest % "test",
    sprayTestkit % "test",
    mockitoAll % "test",
    slf4jLog4j12 % "test"
  ),
  plugins = Seq(
    bundleWarPlugin,
    sbtCompilerPlugin,
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
