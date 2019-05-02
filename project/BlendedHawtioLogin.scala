import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedHawtioLogin extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.hawtio.login"
    override val description = "Adding required imports to the hawtio war bundle"

    override def bundle = super.bundle.copy(
      importPackage = Seq(
        "blended.security.boot",
        "com.sun.jndi.ldap;resolution:=optional"
      ),
      exportPackage = Seq(),
      additionalHeaders = Map(
        "Fragment-Host" -> "io.hawt.hawtio-web"
      )
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedSecurityBoot.project
    )
  }
}
