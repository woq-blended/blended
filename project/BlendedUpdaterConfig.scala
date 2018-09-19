import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtcrossproject.CrossPlugin.autoImport._
import scalajscrossproject.ScalaJSCrossPlugin.autoImport._

private object BlendedUpdaterConfigCross {

  private[this] val builder = sbtcrossproject
    .CrossProject("blendedUpdaterConfig", file("blended.updater.config"))(JVMPlatform, JSPlatform)

  val project = builder
    .crossType(CrossType.Full)
    .build()
}

object BlendedUpdaterConfigJs extends ProjectHelper {

  override val project = BlendedUpdaterConfigCross.project.js.settings(
    Seq(
      libraryDependencies ++= Seq(
        "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
        "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % "test"
      )
    )
  ).dependsOn(
    BlendedSecurityJs.project
  )

}

object BlendedUpdaterConfigJvm extends ProjectHelper {

  private[this] def helper = new ProjectSettings(
    "blended.updater.config",
    "Configurations for Updater and Launcher",
    deps = Seq(
      Dependencies.prickle,
      Dependencies.typesafeConfig,
      Dependencies.scalatest % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test"
    ),
    adaptBundle = b => b.copy(
      exportPackage = Seq(
        b.bundleSymbolicName,
        s"${b.bundleSymbolicName}.json",
        s"${b.bundleSymbolicName}.util",
        "blended.launcher.config"
      )
    )
  ) {

    override def projectFactory: () => Project = { () =>
      BlendedUpdaterConfigCross.project.jvm.settings(
        Seq(
          name := "blendedUpdaterConfigJvm"
        )
      )
    }
  }

  override val project = helper.baseProject
    .dependsOn(
      BlendedUtilLogging.project,
      BlendedSecurityJvm.project,

      BlendedTestsupport.project % "test"
    )
}
