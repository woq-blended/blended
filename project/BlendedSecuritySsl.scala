import sbt._
import sbt.Keys._
import com.typesafe.sbt.osgi.OsgiKeys._

object BlendedSecuritySsl extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.ssl",
    description = "Bundle to provide simple Server Certificate Management.",
    deps = Seq(
      Dependencies.domino.intransitive(),
      Dependencies.bouncyCastleBcprov,
      Dependencies.bouncyCastlePkix,

      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.scalatest % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.CertificateActivator"
    )
  ) {

    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(

      embeddedJars := {
        (Compile/externalDependencyClasspath).value
          .map(_.data)
          .filter(f =>
            f.getName().startsWith("bcprov") ||
            f.getName().startsWith("bcpkix")
          )
      }
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedUtilLogging.project,
    BlendedUtil.project,
    BlendedMgmtBase.project,

    BlendedTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test"
  )
}
