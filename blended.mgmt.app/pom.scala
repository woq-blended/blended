import org.sonatype.maven.polyglot.scala.model._
import scala.collection.immutable.Seq

//#include ../blended.build/build-versions.scala
//#include ../blended.build/build-dependencies.scala
//#include ../blended.build/build-plugins.scala
//#include ../blended.build/build-common.scala

BlendedModel(
  gav = blendedMgmtApp,
  packaging = "war",
  description = "The blended management web application.",
  // We don't need those dependencies, but we use them for editing in Eclipse
//  dependencies = Seq(
//    "com.github.japgolly.scalajs-react" %%% "core" % Versions.scalajsReact,
//    "com.github.japgolly.scalajs-react" %%% "extra" % Versions.scalajsReact,
//    "com.github.japgolly.scalacss" %%% "ext-react" % Versions.scalaCss,
//
//    "org.scala-js" %%% "scalajs-dom" % Versions.scalajsDom,
//    BlendedVersions.blendedGroupId %%% "blended.updater.config" % BlendedVersions.blendedVersion,
//    "com.github.benhutchison" %%% "prickle" % BlendedVersions.prickle,
//    "com.olvind" %%% "scalajs-react-components" % "0.8.1",
//
//    "com.github.japgolly.scalajs-react" %%% "test" % Versions.scalajsReact % "test",
//    "org.scalatest" %%% "scalatest" % BlendedVersions.scalaTestVersion % "test"
//  ),
  plugins = Seq(
    Plugin(
      gav = Plugins.exec,
      executions = Seq(
        execExecution_compileJs(
        		execId = "compileJS",
        		phase = "compile",
        		args = List("-batch", "fastOptJS::webpack")
        )
      )
    ),
    Plugin(
      gav = Plugins.scala,
      executions = Seq(
        scalaExecution_prepareSbt
      )
    ),
    Plugin(
      gav = Plugins.assembly,
      executions = Seq(
        Execution(
          id = "asset",
          phase = "prepare-package",
          goals = Seq(
            "single"
          )
        )
      ),
      configuration = Config(
        descriptors = Config(
          descriptor = "src/main/assembly/assets.xml"
        )
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
            directory = "${project.build.directory}/${project.artifactId}-${project.version}-assets/${project.artifactId}-${project.version}",
            targetPath = "/",
            includes = Config(
              include = "**/*"
            )
          )
        )
      )
    )
  )
)
