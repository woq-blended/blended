import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.packager.universal.{UniversalDeployPlugin, UniversalPlugin}
import sbt.Keys._
import sbt._
import sbt.librarymanagement.InclExclRule
import blended.sbt.Dependencies

object BlendedSecurityScepStandalone extends ProjectFactory {

  private[this] val libDir = "libs"

  implicit class ImplicitModuleId(moduleId: ModuleID) {
    def pure: ModuleID = moduleId.withExclusions(Vector(InclExclRule()))
  }

  private[this] val helper = new ProjectSettings(
    projectName = "blended.security.scep.standalone",
    description = "Standalone client to manage certificates via SCEP",
    osgi = false,
    deps = Seq(
      Dependencies.felixConnect,
      Dependencies.domino.pure,
      Dependencies.typesafeConfig.pure,
      Dependencies.slf4j.pure,
      Dependencies.orgOsgi.pure,
      Dependencies.cmdOption.pure,
      Dependencies.jcip.pure,
      Dependencies.jscep.pure,
      Dependencies.bouncyCastlePkix,
      Dependencies.bouncyCastleBcprov,
      Dependencies.commonsIo,
      Dependencies.commonsLang2,
      Dependencies.commonsCodec,
      Dependencies.logbackCore,
      Dependencies.logbackClassic,
      Dependencies.jclOverSlf4j,
      Dependencies.scalatest % "test"
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

        Universal/mappings += (Compile/packageBin).value -> "scep-client.jar",
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
    BlendedUpdaterConfigJvm.project,

    BlendedTestsupport.project % "test"
  )
}
