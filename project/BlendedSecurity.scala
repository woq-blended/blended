import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtcrossproject.CrossPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._
import scoverage.ScoverageKeys.coverageEnabled
import blended.sbt.Dependencies
import phoenix.{ProjectConfig, ProjectFactory}
import sbtcrossproject.CrossProject

private object BlendedSecurityCross {

  private[this] val builder = sbtcrossproject
    .CrossProject("blendedSecurity", file("blended.security"))(JVMPlatform, JSPlatform)

  val project: CrossProject = builder
    .crossType(CrossType.Full)
    .build()
}

object BlendedSecurityJs extends ProjectFactory {
  object config extends ProjectConfig with CommonSettings with PublishConfig {
    override val projectName = "blended.security"
    override def createProject(): Project = BlendedSecurityCross.project.js
    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
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
  object config extends ProjectSettings {
    override val projectName = "blended.security"
    override val description = "Configuration bundle for the security framework."

    override def deps = Seq(
      Dependencies.prickle,
      Dependencies.scalatest % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.SecurityActivator",
      exportPackage = Seq(
        projectName,
        s"${projectName}.json"
      )
    )

    override def createProject(): Project = BlendedSecurityCross.project.jvm

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      name := "blendedSecurityJvm",
      moduleName := "blended.security"
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedDomino.project,
      BlendedUtil.project,
      BlendedSecurityBoot.project
    )
  }
}
