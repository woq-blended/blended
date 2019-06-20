import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedContainerContextApi extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName = "blended.container.context.api"
    override val description = "The API for the Container Context and Identifier Services"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.typesafeConfig
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      importPackage = Seq(
        "blended.launcher.runtime;resolution:=optional"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedSecurityCrypto.project
    )
  }
}
