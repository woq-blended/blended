object BlendedSecurityBoot extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.boot",
    description = "A delegating Login Module for the Blended Container",
    deps = Seq(
      Dependencies.orgOsgi
    ),
    adaptBundle = b => b.copy(
      additionalHeaders = Map("Fragment-Host" -> "system.bundle;extension:=framework")
    )
  )

  override val project = helper.baseProject
}
