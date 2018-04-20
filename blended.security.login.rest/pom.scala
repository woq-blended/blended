import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.securityLoginRest,
  packaging = "war",
  description = "A REST service providing login services and web token management",
  dependencies = Seq(
    scalaLib % "provided",
    Blended.domino,
    Blended.spray,
    sprayJson,
    scalaTest % "test",
    sprayTestkit % "test"
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
