import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedPersistence extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.persistence"
    override val description = "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles"

    override def deps = Seq(
      Dependencies.slf4j,
      Dependencies.domino,
      Dependencies.scalatest % "test",
      Dependencies.mockitoAll % "test",
      Dependencies.slf4jLog4j12 % "test"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedTestsupport.project % "test"
    )
  }
}
