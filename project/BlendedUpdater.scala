import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedUpdater extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.updater"
    override val description = "OSGi Updater"

    override def deps = Seq(
      Dependencies.orgOsgi,
      Dependencies.domino,
      Dependencies.akkaOsgi,
      Dependencies.slf4j,
      Dependencies.typesafeConfig,
      Dependencies.akkaTestkit % Test,
      Dependencies.scalatest % Test,
      Dependencies.felixFramework % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.felixGogoRuntime % Test,
      Dependencies.felixGogoShell % Test,
      Dependencies.felixGogoCommand % Test,
      Dependencies.felixFileinstall % Test,
      Dependencies.mockitoAll % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.BlendedUpdaterActivator",
      importPackage = Seq(
        "blended.launcher.runtime;resolution:=optional"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUpdaterConfigJvm.project,
      BlendedLauncher.project,
      BlendedMgmtBase.project,
      BlendedContainerContextApi.project,
      BlendedAkka.project,
      BlendedTestsupport.project % Test
    )
  }
}
