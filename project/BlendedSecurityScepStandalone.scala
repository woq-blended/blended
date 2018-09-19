import sbt._
import sbt.Keys._
import com.typesafe.sbt.packager.universal.UniversalPlugin
import com.typesafe.sbt.packager.universal.UniversalDeployPlugin
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._

object BlendedSecurityScepStandalone extends ProjectFactory {

  private[this] val libDir = "libs"

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.scep.standalone",
    description = "Standalone client to manage certificates via SCEP",
    osgi = false,
    deps = Seq(
      Dependencies.felixConnect,
      Dependencies.domino.intransitive,
      Dependencies.typesafeConfig.intransitive,
      Dependencies.slf4j.intransitive,
      Dependencies.orgOsgi.intransitive,
      Dependencies.cmdOption.intransitive,
      Dependencies.jcip.intransitive,
      Dependencies.jscep.intransitive,
      Dependencies.bouncyCastlePkix,
      Dependencies.bouncyCastleBcprov,
      Dependencies.commonsIo,
      Dependencies.commonsLang2,
      Dependencies.commonsCodec,
      Dependencies.logbackCore,
      Dependencies.logbackClassic
    )
  ) {

    override def extraPlugins: Seq[AutoPlugin] = Seq(
      UniversalPlugin,
      UniversalDeployPlugin
    )

    override def settings: Seq[sbt.Setting[_]] =

      defaultSettings ++ Seq(
        Universal/mappings ++= (Compile/dependencyClasspathAsJars).value
          .filter(_.data.isFile())
          .map(_.data)
          .map(f => f -> s"$libDir/${f.getName()}"),

        Universal/mappings += (Compile/packageBin/artifactPath).value -> "scep-client.jar",
        Universal/mappings += baseDirectory.value / "README.adoc" -> "README.adoc",

        Compile/packageOptions += {

          val appClasspath : Seq[String] = (Compile/dependencyClasspathAsJars).value
            .filter(_.data.isFile())
            .map( af => s"$libDir/${af.data.getName()}")

          Package.ManifestAttributes(
            "Class-Path" -> appClasspath.mkString(" ")
          )
        }
      ) ++
        addArtifact(Universal/packageBin/artifact, Universal/packageBin).settings ++
        Seq(
          packageBin := (Universal/packageBin).dependsOn(Compile/packageBin).value,
          publishM2 := (Universal/publishM2).dependsOn(Compile/packageBin).value,
          publishLocal := publishLocal.dependsOn(Universal/publishLocal).value
        )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedSecurityScep.project,
    BlendedSecuritySsl.project,
    BlendedContainerContextImpl.project,
    BlendedUtilLogging.project,
    BlendedContainerContextApi.project,
    BlendedDomino.project,
    BlendedUpdaterConfigJvm.project
  )
}
