import com.typesafe.sbt.osgi.OsgiKeys._
import sbt.Keys._
import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedSecuritySsl extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.security.ssl"
    override val description = "Bundle to provide simple Server Certificate Management."

    override def deps = Seq(
      Dependencies.domino.intransitive(),
      Dependencies.bouncyCastleBcprov,
      Dependencies.bouncyCastlePkix,

      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.scalatest % Test,
      Dependencies.scalacheck % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.CertificateActivator"
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / javaOptions +=
        "-Djava.security.properties=" + ((Test / classDirectory).value / "container/security.properties").getAbsolutePath(),

      embeddedJars := {
        (Compile / externalDependencyClasspath).value
          .map(_.data)
          .filter(f =>
            f.getName().startsWith("bcprov") ||
              f.getName().startsWith("bcpkix"))
      }
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedUtilLogging.project,
      BlendedUtil.project,
      BlendedMgmtBase.project,

      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )
  }
}
