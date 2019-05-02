import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedSecurityBoot extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.security.boot"
    override val description = "A delegating Login Module for the Blended Container"

    override def deps = Seq(
      Dependencies.orgOsgi
    )

    override def bundle = super.bundle.copy(
      additionalHeaders = Map("Fragment-Host" -> "system.bundle;extension:=framework"),
      // This is required to omit the Import-Package header in the OSGi manifest
      // because framework extensions are not allowed to have imports
      importPackage = Seq(""),
      defaultImports = false
    )
  }
}
