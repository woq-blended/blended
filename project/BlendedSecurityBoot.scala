import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt.librarymanagement.ModuleID

object BlendedSecurityBoot extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.security.boot"
    override val description : String = "A delegating Login Module for the Blended Container"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.orgOsgi
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      additionalHeaders = Map("Fragment-Host" -> "system.bundle;extension:=framework"),
      // This is required to omit the Import-Package header in the OSGi manifest
      // because framework extensions are not allowed to have imports
      importPackage = Seq(""),
      defaultImports = false
    )
  }
}
