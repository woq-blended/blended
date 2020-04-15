import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import com.typesafe.sbt.osgi.OsgiKeys._
import phoenix.ProjectFactory
import sbt.Keys._
import sbt._

object BlendedSecurityLoginImpl extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.security.login.impl"
    override val description : String = "Implementation of the Login backend."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.jjwt,
      Dependencies.bouncyCastleBcprov,
      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.LoginActivator",
      importPackage = Seq("android.*;resolution:=optional"),
      privatePackage = Seq(projectName),
      exportPackage = Seq()
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      embeddedJars := {
        (Compile / externalDependencyClasspath).value.map(_.data).filter { f =>
          f.getName.startsWith("bcprov") || f.getName().startsWith("jjwt")
        }
      }
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedSecurityLoginApi.project,

      BlendedTestsupport.project.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
