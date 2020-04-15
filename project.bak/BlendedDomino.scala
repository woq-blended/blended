import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedDomino extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName = "blended.domino"
    override val description = "Blended Domino extension for new Capsule scopes."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.typesafeConfig,
      Dependencies.domino
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedContainerContextApi.project
    )
  }
}
