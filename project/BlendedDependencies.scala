import sbt._
import sbt.plugins.SbtPlugin
import scoverage.ScoverageSbtPlugin.autoImport._

object BlendedDependencies extends ProjectFactory {

  val exportDependenciesFile = taskKey[File]("Export project dependencies as Scala file")

  private val helper = new ProjectSettings(
    projectName = "blended.dependencies",
    description = "Blended dependencies",
    osgi = false
  ) {

    override def settings: Seq[sbt.Setting[_]] =
      super.settings ++
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

    override def extraPlugins: Seq[AutoPlugin] = Seq(
      SbtPlugin
    )

  }

  override val project = helper.baseProject
}
