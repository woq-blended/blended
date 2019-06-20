import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedPersistence extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.persistence"
    override val description : String = "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles"

    override def deps : Seq[ModuleID] = Seq(
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
