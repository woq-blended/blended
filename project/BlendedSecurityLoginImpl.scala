import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.OsgiKeys._

object BlendedSecurityLoginImpl extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.login.impl",
    description = "Implementation of the Login backend.",
    deps = Seq(
      Dependencies.bouncyCastleBcprov,
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.LoginActivator",
      importPackage = Seq("android.*;resolution:=optional"),
      privatePackage = Seq(b.bundleSymbolicName)
    )
  ) {

    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      embeddedJars := {
        (Compile/externalDependencyClasspath).value.map(_.data).filter { f =>
          f.getName.startsWith("bcprov")
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
