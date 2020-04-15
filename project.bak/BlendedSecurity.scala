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

private object BlendedSecurityCross {

  private[this] val builder = sbtcrossproject
    .CrossProject("blendedSecurity", file("blended.security"))(JVMPlatform, JSPlatform)

  val project : CrossProject = builder
    .crossType(CrossType.Full)
    .build()
}

object BlendedSecurityJs extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectConfig with CommonSettings with PublishConfig {
  // scalastyle:on object.name
    override val projectName = "blended.security"
    override def createProject() : Project = BlendedSecurityCross.project.js
    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      name := projectName,
      moduleName := projectName,
      libraryDependencies ++= Seq(
        "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
        "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % Test
      ),
      coverageEnabled := false
    )
  }
}

object BlendedSecurityJvm extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName = "blended.security"
    override val description = "Configuration bundle for the security framework."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.prickle,
      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.SecurityActivator",
      exportPackage = Seq(
        projectName,
        s"$projectName.json"
      )
    )

    override def createProject() : Project = BlendedSecurityCross.project.jvm

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      name := "blendedSecurityJvm",
      moduleName := "blended.security"
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedDomino.project,
      BlendedUtil.project,
      BlendedSecurityBoot.project
    )
  }
}
