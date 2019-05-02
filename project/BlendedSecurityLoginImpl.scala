import com.typesafe.sbt.osgi.OsgiKeys._
import sbt.Keys._
import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedSecurityLoginImpl extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.security.login.impl"
    override val description = "Implementation of the Login backend."

    override def deps = Seq(
      Dependencies.jjwt,
      Dependencies.bouncyCastleBcprov,
      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.LoginActivator",
      importPackage = Seq("android.*;resolution:=optional"),
      privatePackage = Seq(projectName),
      exportPackage = Seq()
    )

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      embeddedJars := {
        (Compile / externalDependencyClasspath).value.map(_.data).filter { f =>
          f.getName.startsWith("bcprov") || f.getName().startsWith("jjwt")
        }
      }
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedSecurityLoginApi.project,

      BlendedTestsupport.project.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
