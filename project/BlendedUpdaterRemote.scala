import sbt._

object BlendedUpdaterRemote extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.updater.remote",
    "OSGi Updater remote handle support"
  ) {
    override val libDeps = Seq(
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

    override lazy val bundle: BlendedBundle = defaultBundle.copy(
      bundleActivator = s"${prjName}.internal.RemoteUpdaterActivator"
    )
  }
  override  val project  = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedPersistence.project,
    BlendedUpdaterConfigJvm.project,
    BlendedMgmtBase.project,
    BlendedLauncher.project,
    BlendedContainerContextApi.project,
    BlendedAkka.project,
    BlendedTestsupport.project % "test"
  )
}
