import sbt._

object BlendedUpdaterTools extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.updater.tools",
    "Configurations for Updater and Launcher"
  ) {
    override val libDeps = Seq(
      Dependencies.typesafeConfig,
      Dependencies.cmdOption,
      Dependencies.scalatest % "test"
    )

    override lazy val bundle: BlendedBundle = defaultBundle.copy(
      privatePackage = defaultBundle.privatePackage ++ Seq(s"${prjName}.configbuilder")
    )
  }

  override  val project  = helper.baseProject.dependsOn(
    BlendedUpdaterConfigJvm.project
  )
}
