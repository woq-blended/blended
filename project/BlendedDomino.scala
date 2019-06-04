import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedDomino extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.domino"
    override val description = "Blended Domino extension for new Capsule scopes."

    override def deps = Seq(
      Dependencies.typesafeConfig,
      Dependencies.domino
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedContainerContextApi.project
    )
  }
}
