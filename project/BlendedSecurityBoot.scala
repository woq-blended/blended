object BlendedSecurityBoot extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.boot",
    description = "A delegating Login Module for the Blended Container",
    deps = Seq(
      Dependencies.orgOsgi
    ),
    osgiDefaultImports = false,
    adaptBundle = b => b.copy(
      additionalHeaders = Map("Fragment-Host" -> "system.bundle;extension:=framework"),
      // This is required to omit the Import-Package header in the OSGi manifest
      // because framework extensions are not allowed to have imports
      importPackage = Seq("")
    )
  )

  override val project = helper.baseProject
}
