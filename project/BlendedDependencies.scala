import phoenix.ProjectFactory
import sbt._
import sbt.plugins.SbtPlugin
import scoverage.ScoverageSbtPlugin.autoImport._

object BlendedDependencies extends ProjectFactory {

  val exportDependenciesFile = taskKey[File]("Export project dependencies as Scala file")

  object config extends ProjectSettings {
    override val projectName = "blended.dependencies"
    override val description = "Blended dependencies"
    override val osgi = false

    override def settings : Seq[sbt.Setting[_]] = super.settings ++
      Seq(
        coverageEnabled := false,
        exportDependenciesFile := {
          file("project/Dependencies.scala")
        }
      ) ++
        addArtifact(
          Artifact(
            name = projectName,
            `type` = "dependencies",
            extension = "scala",
            classifier = "dependencies"
          ),
          exportDependenciesFile
        )

    override def plugins : Seq[AutoPlugin] = super.plugins ++ Seq(
      SbtPlugin
    )
  }
}
