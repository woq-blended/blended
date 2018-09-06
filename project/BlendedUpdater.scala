object BlendedUpdater extends ProjectSettings(
  prjName = "blended.updater",
  desc = "OSGi Updater"
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
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
    bundleActivator = s"${prjName}.internal.BlendedUpdaterActivator",
    importPackage = Seq("blended.launcher.runtime;resolution:=optional")
  )
}
