import java.nio.file.{Files, StandardCopyOption}

import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.packager.universal.{UniversalDeployPlugin, UniversalPlugin}
import de.wayofquality.sbt.filterresources.FilterResources
import de.wayofquality.sbt.filterresources.FilterResources.autoImport._
import phoenix.ProjectFactory
import sbt.Keys._
import sbt._

object BlendedLauncher extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName : String = "blended.launcher"
    override val description : String = "Provide an OSGi Launcher"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.cmdOption,
      Dependencies.orgOsgi,
      Dependencies.typesafeConfig,
      Dependencies.logbackCore,
      Dependencies.logbackClassic,
      Dependencies.commonsDaemon,
      Dependencies.splunkjava,
      Dependencies.httpCore,
      Dependencies.httpCoreNio,
      Dependencies.httpComponents,
      Dependencies.httpAsync,
      Dependencies.commonsLogging,
      Dependencies.jsonSimple,

      Dependencies.scalatest % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      importPackage = Seq(
        "org.apache.commons.daemon;resolution:=optional",
        "de.tototec.cmdoption.*;resolution:=optional"
      ),
      privatePackage = Seq(
        s"$projectName.internal",
        s"$projectName.jvmrunner",
        s"$projectName.runtime"
      )
    )

    override def plugins : Seq[AutoPlugin] = super.plugins ++ Seq(
      UniversalPlugin,
      UniversalDeployPlugin,
      FilterResources
    )

    private def scalaSettings : Seq[sbt.Setting[_]] = Seq(
      Compile / filterSources := Seq(baseDirectory.value / "src" / "runner" / "resources"),
      Compile / filterTargetDir := target.value / "runner",
      Compile / filterRegex := "[@]([^\\n]+?)[@]",
      Compile / filterProperties := Map(
        "blended.launcher.version" -> version.value,
        "blended.updater.config.version" -> version.value,
        "blended.util.logging.version" -> version.value,
        "blended.security.crypto.version" -> version.value,
        "cmdoption.version" -> Dependencies.cmdOption.revision,
        "org.osgi.core.version" -> Dependencies.orgOsgi.revision,
        "scala.binary.version" -> scalaBinaryVersion.value,
        "scala.library.version" -> Dependencies.scalaVersion,
        "typesafe.config.version" -> Dependencies.typesafeConfig.revision,
        "slf4j.version" -> Dependencies.slf4jVersion,
        "logback.version" -> Dependencies.logbackClassic.revision,
        "splunkjava.version" -> Dependencies.splunkjava.revision,
        "httpcore.version" -> Dependencies.httpCore.revision,
        "httpcorenio.version" -> Dependencies.httpCoreNio.revision,
        "httpcomponents.version" -> Dependencies.httpComponents.revision,
        "httpasync.version" -> Dependencies.httpAsync.revision,
        "commonslogging.version" -> Dependencies.commonsLogging.revision,
        "jsonsimple.version" -> Dependencies.jsonSimple.revision
      ),
      Test / resourceGenerators += Def.task {
        val frameworks : Seq[ModuleID] = Seq(
          "org.apache.felix" % "org.apache.felix.framework" % "5.0.0",
          "org.apache.felix" % "org.apache.felix.framework" % "5.6.10",

          "org.eclipse" % "org.eclipse.osgi" % "3.8.0.v20120529-1548",
          "org.osgi" % "org.eclipse.osgi" % "3.10.100.v20150529-1857",
          "org.eclipse.platform" % "org.eclipse.osgi" % "3.12.50",
          "org.eclipse.birt.runtime" % "org.eclipse.osgi" % "3.9.1.v20130814-1242",
          "org.eclipse.birt.runtime" % "org.eclipse.osgi" % "3.10.0.v20140606-1445"
        )

        val osgiDir = target.value / "test-osgi"

        BuildHelper.deleteRecursive(osgiDir)
        Files.createDirectories(osgiDir.toPath)

        val files = frameworks
          .map { mid => BuildHelper.resolveModuleFile(mid, target.value) }
          .collect {
            case f if f.nonEmpty => f
          }
          .flatten

        files.map { f =>
          val tf = new File(osgiDir, f.getName)
          Files.copy(f.toPath, tf.toPath, StandardCopyOption.REPLACE_EXISTING)
          tf
        }
      }.taskValue
    )

    private def packageSettings : Seq[sbt.Setting[_]] = Seq(
      Universal / topLevelDirectory := None,

      Universal / mappings += {
        val packaged = (Compile / packageBin).value
        packaged -> s"lib/${packaged.getName}"
      },
      Universal / mappings ++= {
        val dir = baseDirectory.value / "src" / "runner" / "binaryResources"
        PathFinder(dir).**("***").pair(relativeTo(dir))
      },
      Universal / mappings ++= (Compile / dependencyClasspathAsJars).value
        .map(_.data)
        .filter(_.isFile)
        .filterNot(_.getName().startsWith("akka-actor"))
        .filterNot(_.getName().startsWith("akka-slf4j"))
        .filterNot(_.getName().startsWith("prickle"))
        .map { f =>
          f -> s"lib/${f.getName()}"
        },
      Universal / mappings ++= (Compile / filterResources).value,
      Universal / packageBin / mainClass := None,

      packagedArtifacts ++= (Universal / packagedArtifacts).value
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ scalaSettings ++ packageSettings

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedUpdaterConfigJvm.project,
      BlendedSecurityCrypto.project,

      BlendedTestsupport.project % Test
    )
  }
}
