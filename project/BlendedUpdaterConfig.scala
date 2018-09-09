import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtcrossproject.CrossPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._

object BlendedUpdaterConfigCross  {

  val project = crossProject(JVMPlatform, JSPlatform)
    .crossType(CrossType.Full)
    .withoutSuffixFor(JVMPlatform)
    .in(file("blended.updater.config"))
}

object BlendedUpdaterConfigJs extends ProjectHelper {


  override  val project  = BlendedUpdaterConfigCross.project.js.settings(
    Seq(
      libraryDependencies ++= Seq(
        "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
        "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % "test"
      )
    )
  )
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
      .dependsOn(BlendedUtilLogging.project)
  }

  override  val project  = helper.baseProject
}
