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
    Plugin(
      gav = Plugins.clean,
      configuration = Config(
        filesets = Config(
          fileset = Config(
            directory = "node_modules"
          )
        )
      )
    ),
    execPlugin("npm", "npm-install", "process-classes", List("install")),
    execPlugin("node", "webpack", "prepare-package", List("node_modules/webpack/bin/webpack.js")),
    prepareSbtPlugin,
    compileJsPlugin(
      execId = "compileJS",
      phase = "compile",
      args = List("-batch", "fullOptJS")
    ),
    bundleWarPlugin,
    Plugin(
      gav = Plugins.war,
      configuration = Config(
        archive = Config(
          manifestFile = "${project.build.outputDirectory}/META-INF/MANIFEST.MF"
        ),
        webResources = Config(
          resource = Config(
            directory = "${project.build.directory}/assets",
            targetPath = "/assets",
            includes = Config(
              include = "**/*"
            )
          ),
          resource = Config(
            directory = "${project.basedir}",
            targetPath = "/",
            includes = Config(
              include = "index.html"
            )
          )
        )
      )
    )
  )
)
