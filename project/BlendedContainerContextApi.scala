import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedContainerContextApi extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.container.context.api"
    override val description = "The API for the Container Context and Identifier Services"

    override def deps = Seq(
      Dependencies.typesafeConfig
    )

    override def bundle = super.bundle.copy(
      importPackage = Seq(
        "blended.launcher.runtime;resolution:=optional"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedSecurityCrypto.project
    )
  }
}
