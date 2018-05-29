import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = Blended.akkaHttpApi,
  packaging = "bundle",
  description = "Package the complete Spray API into a bundle.",
  dependencies = Seq(
    Deps.scalaLib % "provided",
    Deps.akkaHttp,
    Deps.akkaHttpCore,
    Deps.akkaParsing
  ),
  plugins = Seq(
    Plugin(
      mavenBundlePlugin.gav,
      extensions = true,
      inherited = true,
      configuration = Config(
        instructions = new Config(Seq(
          "_include" -> Option("osgi.bnd"),
          "Embed-Dependency" -> Option("*;scope=compile"),
          "_exportcontents" -> Option(
            "akka.http.*;version="+ BlendedVersions.akkaHttpVersion + ";-split-package:=merge-first,"
          ),
          "Embed-Transitive" -> Option("true")
        ))
      )
    )
  )
)
