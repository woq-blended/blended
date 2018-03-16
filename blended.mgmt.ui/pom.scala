import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala
//#include project/Versions.scala

BlendedModel(
  gav = blendedMgmtUi,
  packaging = "war",
  description = "The blended management UI.",
  // We don't need those dependencies, but we use them for editing in Eclipse
  dependencies = Seq(
    "com.github.japgolly.scalajs-react" %%% "core" % Versions.scalajsReact,
    "com.github.japgolly.scalajs-react" %%% "extra" % Versions.scalajsReact,
    "com.github.japgolly.scalacss" %%% "ext-react" % Versions.scalaCss,

    "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom,
    BlendedVersions.blendedGroupId %%% "blended.updater.config" % BlendedVersions.blendedVersion,
    "com.github.benhutchison" %%% "prickle" % BlendedVersions.prickle,
    "com.olvind" %%% "scalajs-react-components" % "0.8.1",

    "com.github.japgolly.scalajs-react" %%% "test" % Versions.scalajsReact % "test",
    "org.scalatest" %%% "scalatest" % BlendedVersions.scalaTestVersion % "test"
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
    Plugin(
      gav = Plugins.exec,
      executions = Seq(
        execExecution(
            executable = "npm", 
            execId = "npm-install", 
            phase = "process-classes", 
            args = List("install")
        ),
        execExecution(
            executable = "node", 
            execId = "webpack", 
            phase = "prepare-package", 
            args = List("node_modules/webpack/bin/webpack.js")
        ),
        execExecution_compileJs(
        		execId = "compileJS",
        		phase = "compile",
        		args = List("-batch", "fullOptJS")
        )
      )
    ),
    Plugin(
      gav = Plugins.scala,
      executions = Seq(
        scalaExecution_prepareSbt
      )
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
