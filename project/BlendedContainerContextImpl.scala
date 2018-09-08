object BlendedContainerContextImpl
  extends ProjectSettings(
    prjName = "blended.container.context.impl",
    desc = "A simple OSGI service to provide access to the container's config directory",
    libDeps = Seq(
      Dependencies.orgOsgiCompendium,
      Dependencies.orgOsgi,
      Dependencies.domino,
      Dependencies.slf4j,
      Dependencies.julToSlf4j,
      Dependencies.scalatest % "test",
      Dependencies.mockitoAll % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )
  ) {

  override def bundle: BlendedBundle = super.bundle.copy(
    bundleActivator = s"${prjName}.internal.ContainerContextActivator",
    importPackage = Seq("blended.launcher.runtime;resolution:=optional")
  )

}
