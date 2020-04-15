import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import com.typesafe.sbt.osgi.OsgiKeys._
import phoenix.ProjectFactory
import sbt.Keys._
import sbt._

object BlendedSecurityScep extends ProjectFactory {
  private[this] val embeddedPrefixes : Seq[String] = Seq(
    "bcprov", "bcpkix", "commons-io", "commons-lang", "commons-codec", "jcip-annotations",
    "jscep"
  )

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.security.scep"
    override val description : String = "Bundle to manage the container certificate via SCEP."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.bouncyCastlePkix,
      Dependencies.bouncyCastleBcprov,
      Dependencies.commonsIo,
      Dependencies.commonsLang2,
      Dependencies.commonsCodec,
      Dependencies.jcip,
      Dependencies.jscep,

      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.scalatest % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.ScepActivator"
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      embeddedJars := (Compile / externalDependencyClasspath).value
        .map(af => af.data)
        .filter { f => embeddedPrefixes.exists(p => f.getName().startsWith(p)) }

    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedSecuritySsl.project,
      BlendedUtilLogging.project
    )
  }
}
