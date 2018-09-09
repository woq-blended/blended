import java.nio.file.{Files, StandardCopyOption}

import sbt._
import sbt.Keys._
import Dependencies._

import com.typesafe.sbt.osgi.OsgiKeys
import com.typesafe.sbt.packager.universal.UniversalPlugin
import com.typesafe.sbt.packager.universal.UniversalDeployPlugin
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import com.typesafe.sbt.SbtNativePackager.autoImport._
import NativePackagerHelper._

import FilterResources.autoImport._

object BlendedLauncher extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.launcher",
    "Provide an OSGi Launcher"
  ) {
    override val  libDeps = Seq(
      Dependencies.cmdOption,
      Dependencies.orgOsgi,
      Dependencies.typesafeConfig,
      Dependencies.logbackCore,
      Dependencies.logbackClassic,
      Dependencies.commonsDaemon
    )

    override val extraPlugins = Seq(
      UniversalPlugin,
      UniversalDeployPlugin,
      FilterResources
    )
    override val settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(

      Compile/filterSources := Seq(baseDirectory.value / "src" / "runner" / "resources"),
      Compile/filterTargetDir := target.value / "runner",
      Compile/filterRegex := "(@)([^\\n]+?)(@)",
      Compile/filterProperties := Map(
        "blended.launcher.version" -> version.value,
        "blended.updater.config.version" -> version.value,
        "blended.util.logging.version" -> version.value,
        "cmdoption.version" -> cmdOption.revision,
        "org.osgi.core.version" -> orgOsgi.revision,
        "scala.library.version" -> scalaVersion.value,
        "typesafe.config.version" -> typesafeConfig.revision,
        "slf4j.version" -> slf4j.revision,
        "logback.version" -> logbackClassic.revision
      ),
      Test/resourceGenerators += Def.task {
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
          .map{ mid => BuildHelper.resolveModuleFile(mid, target.value) }
          .collect {
            case f if f.nonEmpty => f
          }
          .flatten

        files.map { f =>
          val tf = new File(osgiDir, f.getName)
          Files.copy(f.toPath, tf.toPath, StandardCopyOption.REPLACE_EXISTING)
          tf
        }
      }.taskValue,
    ) ++ Seq(
      Universal/mappings ++= Seq(OsgiKeys.bundle.value).map { f =>
        f -> s"lib/${f.getName}"
      },
      Universal/mappings ++= {
        val dir = baseDirectory.value / "src" / "runner" / "binaryResources"
        PathFinder(dir).**("***").pair(relativeTo(dir))
      },
      Universal/mappings ++= (Compile/dependencyClasspathAsJars).value.filter(_.data.isFile).map{ f =>
        f.data -> s"lib/${f.data.getName}"
      },
      Universal/mappings ++= (Compile/filterResources).value,
      Universal/packageBin/mainClass := None,
    ) ++
      addArtifact(Universal/packageBin/artifact, Universal/packageBin).settings ++
      addArtifact(Universal/packageZipTarball/artifact, Universal/packageZipTarball).settings ++
      Seq(
        publishM2 := publishM2.dependsOn(Universal/publishM2).value,
        publishLocal := publishLocal.dependsOn(Universal/publishLocal).value
      )
  }

  override  val project  = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedUpdaterConfigJvm.project,
    BlendedAkka.project,
    BlendedTestsupport.project % "test"
  )
}
