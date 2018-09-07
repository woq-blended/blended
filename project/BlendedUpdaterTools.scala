import sbt._

object BlendedUpdaterTools extends ProjectSettings(
  prjName = "blended.updater.tools",
  desc = "Configurations for Updater and Launcher",
  libDeps = Seq(
    Dependencies.typesafeConfig,
    Dependencies.cmdOption,
    Dependencies.scalatest % "test"
  )
) {

  override def bundle: BlendedBundle = super.bundle.copy(
    privatePackage = super.bundle.privatePackage ++ Seq(s"${prjName}.configbuilder")
  )

}
