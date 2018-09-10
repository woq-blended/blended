import sbt._

object BlendedUpdaterTools extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.updater.tools",
    "Configurations for Updater and Launcher",
    deps = Seq(
      Dependencies.typesafeConfig,
      Dependencies.cmdOption,
      Dependencies.scalatest % "test"
    ),
    adaptBundle = b => b.copy(
      privatePackage = b.privatePackage ++ Seq(s"${b.bundleSymbolicName}.configbuilder")
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedUpdaterConfigJvm.project
  )
}
