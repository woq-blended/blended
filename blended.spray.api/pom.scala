import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-common.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala

BlendedModel(
  gav = blendedSprayApi,
  packaging = "bundle",
  description = "Package the complete Spray API into a bundle.",
  dependencies = Seq(
    scalaLib % "provided",
    sprayServlet,
    sprayClient,
    sprayRouting,
    sprayJson,
    sprayCaching,
    shapeless,
    concurrentLinkedHashMapLru
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
          "_exportcontents" -> Option("spray.*;version="+ BlendedVersions.sprayVersion + ";-split-package:=merge-first," +
            "akka.spray.*;version="+ BlendedVersions.sprayVersion + ";-split-package:=merge-first," +
            "org.parboiled.*;version=" + BlendedVersions.parboiledVersion + ";-split-package:=merge-first," +
            "shapeless.*;version=" + BlendedVersions.parboiledVersion + ";-split-package:=merge-first"),
          "Embed-Transitive" -> Option("true")
        ))
      )
    )
  )
)
