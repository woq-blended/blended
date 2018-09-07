object BlendedUpdaterRemote
  extends ProjectSettings(
    prjName = "blended.updater.remote",
    desc = "OSGi Updater remote handle support",
    libDeps = Seq(
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
  ) {

  override def bundle: BlendedBundle = super.bundle.copy(
    bundleActivator = s"${prjName}.internal.RemoteUpdaterActivator"
  )

}
