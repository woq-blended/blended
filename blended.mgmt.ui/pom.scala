import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

#include ../blended.build/build-versions.scala
#include ../blended.build/build-dependencies.scala
#include ../blended.build/build-plugins.scala
#include ../blended.build/build-common.scala
#include project/Versions.scala

BlendedModel(
  gav = blendedMgmtUi,
  packaging = "war",
  description = "The blended management UI.",
  dependencies = Seq(
    scalaLib % "provided",
      "com.github.japgolly.scalajs-react" %%% "core" % Versions.scalajsReact % "provided",
      "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom % "provided",
      blendedUpdaterConfig,
      "com.github.benhutchison" %%% "prickle" % BlendedVersions.prickle % "provided",
      "com.github.japgolly.scalajs-react" %%% "test" % Versions.scalajsReact % "provided",
      scalaTest % "test"
  ),
  plugins = Seq(
    prepareSbtPlugin,
    compileJsPlugin(
      execId = "compileJS",
      phase = "compile",
      args = List("-batch", "fastOptJS::webpack")
    ),
    bundleWarPlugin,
    Plugin(
      "org.apache.maven.plugins" % "maven-assembly-plugin" % "2.6",
      executions = Seq(
        Execution(
          id = "prepareWar",
          phase = "prepare-package",
          goals = Seq(
            "single"
          ),
          configuration = Config(
            descriptors = Config(
              descriptor = "src/main/assembly/assembly.xml"
            )
          )
        )
      )
    ),
    Plugin(
      gav = "org.apache.maven.plugins" % "maven-war-plugin",
      configuration = Config(
        archive = Config(
          manifestFile = "${project.build.outputDirectory}/META-INF/MANIFEST.MF"
        ),
        webResources = Config(
          resource = Config(
            directory = "${project.build.directory}/${project.artifactId}-${project.version}-preWar/${project.artifactId}-${project.version}",
            targetPath = "/",
            includes = Config(
              include = "**/*"
            )
          ),
          resource = Config(
            directory = "${project.build.directory}/web/less/main",
            targetPath = "css"
          )
        )
      )
    ),
    Plugin(
      gav = jettyMavenPlugin,
      configuration = Config(
        scanIntervalSeconds = "10",
        webAppSourceDirectory = "target/blended.mgmt.ui-" + BlendedVersions.blendedVersion,
        webApp = Config(
          contextPath = "/management"
        )
      )
    )
  )
)
