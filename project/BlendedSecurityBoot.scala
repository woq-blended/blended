object BlendedSecurityBoot extends ProjectSettings(
  "blended.security.boot",
  "A delegating Login Module for the Blended Container."
) {

  override val libDependencies: Seq[sbt.ModuleID] = Seq(
    Dependencies.orgOsgi
  )

  override val bundle: BlendedBundle = super.bundle.copy(
    additionalHeaders = Map("Fragment-Host" -> "system.bundle;extension:=framework")
  )
}
