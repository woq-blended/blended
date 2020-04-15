import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import de.wayofquality.sbt.filterresources.FilterResources
import de.wayofquality.sbt.filterresources.FilterResources.autoImport._
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import phoenix.{ProjectConfig, ProjectFactory}
import sbt.Keys._
import sbt._
import sbtcrossproject.CrossPlugin.autoImport._
import sbtcrossproject.CrossProject
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._
import scoverage.ScoverageKeys._

private object BlendedWebSocketCross {
  private[this] val builder = sbtcrossproject
    .CrossProject("blendedWebsocket", file("blended.websocket"))(JVMPlatform, JSPlatform)

  val project : CrossProject = builder
    .crossType(CrossType.Full)
    .build()
}

object BlendedWebsocketJs extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectConfig with CommonSettings with PublishConfig {
  // scalastyle:on object.name
    override def projectName: String = "blended.websocket"
    override def createProject(): Project = BlendedWebSocketCross.project.js

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

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedJmxJs.project
    )
  }
}

object BlendedWebsocketJvm extends ProjectFactory {
  //scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName = "blended.websocket"
    override val description = "The web socket server module."

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogLogPackages := Map(
        "blended.websocket" -> "DEBUG",
        "blended.jmx" -> "DEBUG"
      ),
      Test / testlogDefaultLevel := "INFO",
      Compile / filterProperties ++= Map("projectVersion" -> version.value),
      Compile / compile := {
        (Compile / filterResources).value
        (Compile / compile).value
      }
    )

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.akkaHttp,
      Dependencies.akkaHttpCore,

      Dependencies.akkaTestkit % Test,
      Dependencies.sttp % Test,
      Dependencies.sttpAkka % Test,
      Dependencies.scalatest % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.jclOverSlf4j % Test,
      Dependencies.springCore % Test,
      Dependencies.springBeans % Test,
      Dependencies.springContext % Test,
      Dependencies.springExpression % Test,
      Dependencies.commonsLogging % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.WebSocketActivator",
      exportPackage = Seq(
        projectName,
        s"$projectName.json"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedAkkaHttp.project,
      BlendedSecurityLoginApi.project,
      BlendedJmxJvm.project,

      BlendedTestsupport.project % Test,
      BlendedPersistence.project % Test,
      BlendedPersistenceH2.project % Test,
      BlendedSecurityLoginImpl.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedSecurityLoginRest.project % Test
    )

    override def plugins: Seq[AutoPlugin] = super.plugins ++ Seq(FilterResources)

    override def createProject(): Project = BlendedWebSocketCross.project.jvm
  }
}
