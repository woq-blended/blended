import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedPersistence extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.persistence"
    override val description = "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles"

    override def deps = Seq(
      Dependencies.slf4j,
      Dependencies.domino,
      Dependencies.scalatest % Test,
      Dependencies.mockitoAll % Test,
      Dependencies.slf4jLog4j12 % Test
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedTestsupport.project % Test
    )
  }
}
