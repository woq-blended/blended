import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedUpdaterTools extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName = "blended.updater.tools"
    override val description = "Configurations for Updater and Launcher"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.typesafeConfig,
      Dependencies.cmdOption,
      Dependencies.scalatest % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      privatePackage = super.bundle.privatePackage ++ Seq(
        s"$projectName.configbuilder"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUpdaterConfigJvm.project
    )
  }
}
