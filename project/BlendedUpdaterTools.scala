import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedUpdaterTools extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.updater.tools"
    override val description = "Configurations for Updater and Launcher"

    override def deps = Seq(
      Dependencies.typesafeConfig,
      Dependencies.cmdOption,
      Dependencies.scalatest % Test
    )

    override def bundle = super.bundle.copy(
      privatePackage = super.bundle.privatePackage ++ Seq(
      s"${projectName}.configbuilder"
    )
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUpdaterConfigJvm.project
    )
  }
}
