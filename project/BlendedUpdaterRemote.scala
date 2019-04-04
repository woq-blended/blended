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
      Dependencies.akkaTestkit % "test",
      Dependencies.scalatest % "test",
      Dependencies.felixFramework % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.felixGogoRuntime % "test",
      Dependencies.felixGogoShell % "test",
      Dependencies.felixGogoCommand % "test",
      Dependencies.felixFileinstall % "test",
      Dependencies.mockitoAll % "test"
    )

    override def bundle: BlendedBundle = super.bundle.copy(
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
      BlendedTestsupport.project % "test",
      BlendedPersistenceH2.project % "test"
    )
  }
}
