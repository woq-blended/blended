import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.packager.universal.{UniversalDeployPlugin, UniversalPlugin}
import sbt.Keys._
import sbt._
import sbt.librarymanagement.InclExclRule
import blended.sbt.Dependencies
import phoenix.ProjectFactory

import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._

object BlendedSecurityScepStandalone extends ProjectFactory {

  private[this] val libDir = "libs"

  implicit class ImplicitModuleId(moduleId : ModuleID) {
    def pure : ModuleID = moduleId.withExclusions(Vector(InclExclRule()))
  }

  object config extends ProjectSettings {
    override val projectName = "blended.security.scep.standalone"
    override val description = "Standalone client to manage certificates via SCEP"
    override val osgi = false

    override def deps = Seq(
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

      Dependencies.scalatest % Test,
      Dependencies.scalatest % Test
    )

    override def plugins : Seq[AutoPlugin] = super.plugins ++ Seq(
      UniversalPlugin,
      UniversalDeployPlugin
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(

      Test / testlogDefaultLevel := "debug",

      // ensure, we use the JAR of our dependencies, such that we can use them as proper bundles
      Test / dependencyClasspath := {
        (Test / dependencyClasspathAsJars).value
      },

      Universal / mappings ++= (Compile / dependencyClasspathAsJars).value
        .filter(_.data.isFile())
        .map(_.data)
        .map(f => f -> s"$libDir/${f.getName()}"),

      Universal / mappings += (Compile / packageBin).value -> "scep-client.jar",
      Universal / mappings += baseDirectory.value / "README.adoc" -> "README.adoc",

      Compile / packageOptions += {

        val appClasspath : Seq[String] = (Compile / dependencyClasspathAsJars).value
          .filter(_.data.isFile())
          .map(af => s"$libDir/${af.data.getName()}")

        Package.ManifestAttributes(
          "Class-Path" -> appClasspath.mkString(" ")
        )
      }
    ) ++
      addArtifact(Universal / packageBin / artifact, Universal / packageBin).settings ++
      Seq(
        packageBin := (Universal / packageBin).dependsOn(Compile / packageBin).value,
        publishM2 := (Universal / publishM2).dependsOn(Compile / packageBin).value,
        publishLocal := publishLocal.dependsOn(Universal / publishLocal).value,
        Keys.publish := Keys.publish.dependsOn(Universal / Keys.publish).value
      )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedSecurityScep.project,
      BlendedSecuritySsl.project,
      BlendedContainerContextImpl.project,
      BlendedUtilLogging.project,
      BlendedContainerContextApi.project,
      BlendedDomino.project,
      BlendedUpdaterConfigJvm.project,
      BlendedTestsupport.project % Test
    )
  }
}
