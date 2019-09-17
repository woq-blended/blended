import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import com.typesafe.sbt.osgi.OsgiKeys._
import phoenix.ProjectFactory
import sbt.Keys._
import sbt._

object BlendedSecuritySsl extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.security.ssl"
    override val description : String = "Bundle to provide simple Server Certificate Management."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.domino.intransitive(),
      Dependencies.bouncyCastleBcprov,
      Dependencies.bouncyCastlePkix,

      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.scalatest % Test,
      Dependencies.scalacheck % Test,
      Dependencies.springCore % Test,
      Dependencies.springBeans % Test,
      Dependencies.springContext % Test,
      Dependencies.springExpression % Test,
      Dependencies.commonsLogging % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.CertificateActivator"
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
