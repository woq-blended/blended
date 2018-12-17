import com.typesafe.sbt.osgi.OsgiKeys._
import sbt.Keys._
import sbt._
import blended.sbt.Dependencies

object BlendedSecurityScep extends ProjectFactory {
  private[this] val embeddedPrefixes : Seq[String] = Seq(
    "bcprov", "bcpkix", "commons-io", "commons-lang", "commons-codec", "jcip-annotations",
    "jscep"
  )

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.scep",
    description = "Bundle to manage the container certificate via SCEP.",
    deps = Seq(
      Dependencies.bouncyCastlePkix,
      Dependencies.bouncyCastleBcprov,
      Dependencies.commonsIo,
      Dependencies.commonsLang2,
      Dependencies.commonsCodec,
      Dependencies.jcip,
      Dependencies.jscep,

      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test",
      Dependencies.scalatest % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.ScepActivator"
    )
  ){

    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(

      embeddedJars :=  (Compile/externalDependencyClasspath).value
        .map(af => af.data)
        .filter{ f => embeddedPrefixes.exists(p => f.getName().startsWith(p)) }

    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedSecuritySsl.project,
    BlendedUtilLogging.project
  )
}
