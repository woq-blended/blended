import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import phoenix.{ProjectConfig, ProjectFactory}
import sbt.Keys._
import sbt._
import sbtcrossproject.CrossPlugin.autoImport._
import sbtcrossproject.CrossProject
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._
import scoverage.ScoverageKeys.coverageEnabled

private object BlendedUpdaterConfigCross {

  private[this] val builder = sbtcrossproject
    .CrossProject("blendedUpdaterConfig", file("blended.updater.config"))(JVMPlatform, JSPlatform)

  val project : CrossProject = builder
    .crossType(CrossType.Full)
    .build()
}

object BlendedUpdaterConfigJs extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectConfig with CommonSettings with PublishConfig {
  // scalastyle:on object.name
    override val projectName = "blended.updater.config"

    override def createProject() : Project = BlendedUpdaterConfigCross.project.js

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      name := projectName,
      moduleName := projectName,
      libraryDependencies ++= Seq(
        "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
        "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % Test,
        "org.scalacheck" %%% "scalacheck" % Dependencies.scalacheck.revision % Test,
        "org.log4s" %%% "log4s" % Dependencies.log4s.revision % Test
      ),
      coverageEnabled := false
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedSecurityJs.project
    )
  }
}

object BlendedUpdaterConfigJvm extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override def createProject() : Project = BlendedUpdaterConfigCross.project.jvm

    override val projectName = "blended.updater.config"
    override val description = "Configurations for Updater and Launcher"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.prickle,
      Dependencies.typesafeConfig,
      Dependencies.scalatest % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.scalacheck % Test,
      Dependencies.log4s % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      exportPackage = Seq(
        projectName,
        s"$projectName.json",
        s"$projectName.util",
        "blended.launcher.config"
      )
    )

    override def dependsOn : scala.Seq[_root_.sbt.ClasspathDep[_root_.sbt.ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedSecurityJvm.project,

      BlendedTestsupport.project % Test
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      name := "blendedUpdaterConfigJvm",
      moduleName := "blended.updater.config"
    )
  }
}
