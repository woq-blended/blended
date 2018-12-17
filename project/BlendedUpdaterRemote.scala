import sbt._
import blended.sbt.Dependencies

object BlendedUpdaterRemote extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    "blended.updater.remote",
    "OSGi Updater remote handle support",
    deps = Seq(
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
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.RemoteUpdaterActivator"
    )
  )

  override val project = helper.baseProject.dependsOn(
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
