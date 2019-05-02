import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedUpdaterRemote extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.updater.remote"
    override val description = "OSGi Updater remote handle support"

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
      bundleActivator = s"${projectName}.internal.RemoteUpdaterActivator"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedPersistence.project,
      BlendedUpdaterConfigJvm.project,
      BlendedMgmtBase.project,
      BlendedLauncher.project,
      BlendedContainerContextApi.project,
      BlendedAkka.project,
      BlendedTestsupport.project % Test,
      BlendedPersistenceH2.project % Test
    )
  }
}
