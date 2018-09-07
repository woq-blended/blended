object BlendedSecurityBoot extends ProjectSettings(
  prjName = "blended.security.boot",
  desc = "A delegating Login Module for the Blended Container.",
  libDeps = Seq(
    Dependencies.orgOsgi
  )
) {

  override val bundle: BlendedBundle = super.bundle.copy(
    additionalHeaders = Map("Fragment-Host" -> "system.bundle;extension:=framework")
  )
}
