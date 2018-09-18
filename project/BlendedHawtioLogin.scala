import sbt._

object BlendedHawtioLogin extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.hawtio.login",
    description = "Adding required imports to the hawtio war bundle",
    adaptBundle = b => b.copy(
      importPackage = Seq(
        "blended.security.boot",
        "com.sun.jndi.ldap;resolution:=optional"
      ),
      additionalHeaders = Map(
        "Fragment-Host" -> "io.hawt.hawtio-web"
      )
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedSecurityBoot.project
  )
}
