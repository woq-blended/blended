import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtcrossproject.CrossPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._

object BlendedUpdaterConfigCross  {

  private[this ]val builder = sbtcrossproject
    .CrossProject("blendedUpdaterConfig", file("blended.updater.config"))(JVMPlatform, JSPlatform)

  val project = builder
    .crossType(CrossType.Full)
    .build()
}

object BlendedUpdaterConfigJs extends ProjectHelper {

  override  val project  = BlendedUpdaterConfigCross.project.js.settings(
    Seq(
      libraryDependencies ++= Seq(
        "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
        "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % "test"
      )
    )
  ).dependsOn(BlendedSecurityJs.project)
}

object BlendedUpdaterConfigJvm extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.updater.config",
    "Configurations for Updater and Launcher"
  ) {

    override val libDeps = Seq(
      Dependencies.prickle,
      Dependencies.typesafeConfig,
      Dependencies.scalatest % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test"
    )

    override lazy val bundle: BlendedBundle = defaultBundle.copy(
      exportPackage = Seq(
        prjName,
        s"$prjName.json",
        s"$prjName.util",
        "blended.launcher.config"
      )
    )

    override def baseProject = BlendedUpdaterConfigCross.project.jvm
      .settings(settings)
      .enablePlugins(plugins: _*)
      .dependsOn(
        BlendedUtilLogging.project,
        BlendedSecurityJvm.project,
        BlendedTestsupport.project
      )
  }

  override  val project  = helper.baseProject
}
