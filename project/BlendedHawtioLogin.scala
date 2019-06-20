import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedHawtioLogin extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name

    override val projectName : String = "blended.hawtio.login"
    override val description : String = "Adding required imports to the hawtio war bundle"

    override def bundle : OsgiBundle = super.bundle.copy(
      importPackage = Seq(
        "blended.security.boot",
        "com.sun.jndi.ldap;resolution:=optional"
      ),
      exportPackage = Seq(),
      additionalHeaders = Map(
        "Fragment-Host" -> "io.hawt.hawtio-web"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedSecurityBoot.project
    )
  }
}
