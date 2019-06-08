import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.{ProjectConfig, ProjectFactory}
import sbt._
import sbt.Keys._
import sbtcrossproject.CrossPlugin.autoImport._
import sbtcrossproject.CrossProject
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._
import scoverage.ScoverageKeys._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

private object BlendedJmxCross {

  private[this] val builder = sbtcrossproject
    .CrossProject("blendedJmx", file("blended.jmx"))(JVMPlatform, JSPlatform)

  val project : CrossProject = builder
    .crossType(CrossType.Full)
    .build()
}

object BlendedJmxJs extends ProjectFactory {
  //scalastyle:off object.name
  object config extends ProjectConfig with CommonSettings with PublishConfig {
    //scalastyle:on object.name
    override def projectName: String = "blended.jmx"
    override def createProject(): Project = BlendedJmxCross.project.js

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      name := projectName,
      moduleName := projectName,
      libraryDependencies ++= Seq(
        "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
        "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % Test,
        "org.scalacheck" %%% "scalacheck" % Dependencies.scalacheck.revision % Test
      ),
      coverageEnabled := false
    )
  }
}

object BlendedJmxJvm extends ProjectFactory {
  //scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name

    override val projectName = "blended.jmx"
    override val description = "Helper bundle to expose the platform's MBeanServer as OSGI Service."

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.domino,
      Dependencies.prickle,
      Dependencies.typesafeConfig,
      Dependencies.scalatest % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.scalacheck % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.BlendedJmxActivator",
      exportPackage = Seq(
        projectName,
        s"$projectName.json"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test
    )

    override def createProject(): Project = BlendedJmxCross.project.jvm
  }
}
