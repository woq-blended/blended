import com.typesafe.sbt.osgi.OsgiKeys._
import sbt.Keys._
import sbt._
import blended.sbt.Dependencies

object BlendedSecurityLoginImpl extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.login.impl",
    description = "Implementation of the Login backend.",
    deps = Seq(
      Dependencies.jjwt, 
      Dependencies.bouncyCastleBcprov,
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.LoginActivator",
      importPackage = Seq("android.*;resolution:=optional"),
      privatePackage = Seq(b.bundleSymbolicName),
      exportPackage = Seq()
    )
  ) {

    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      embeddedJars := {
        (Compile/externalDependencyClasspath).value.map(_.data).filter { f =>
          f.getName.startsWith("bcprov") || f.getName().startsWith("jjwt")
        }
      }
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedSecurityLoginApi.project,

    BlendedTestsupport.project.project % "test",
    BlendedTestsupportPojosr.project % "test"
  )
}
