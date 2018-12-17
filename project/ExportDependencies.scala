import sbt._

object ExportDependencies {

  val exportDependenciesFile = taskKey[File]("Export project dependencies as Scala file")

  def settings: Seq[Def.Setting[_]] = {

    Seq(
      exportDependenciesFile := {
        file("project/Dependencies.scala")
      }
    ) ++
      addArtifact(
        Artifact(
        name = "blended",
        `type` = "dependencies",
        extension = "scala",
        classifier = "dependencies"
      ),
        exportDependenciesFile
      )
  }

}
