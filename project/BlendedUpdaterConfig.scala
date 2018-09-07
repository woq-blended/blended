import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object BlendedUpdaterConfigJs extends JsProjectSettings() {
  override def libDependencies: Def.Initialize[Seq[librarymanagement.ModuleID]] = Def.setting(
    Seq(
      "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
      "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % "test"
    )
  )
}

object BlendedUpdaterConfigJvm extends ProjectSettings(
  prjName = "blended.updater.config",
  desc = "Configurations for Updater and Launcher"
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    Dependencies.prickle,
    Dependencies.typesafeConfig,
    Dependencies.scalatest % "test",
    Dependencies.logbackClassic % "test",
    Dependencies.logbackCore % "test"
  )

  override def bundle: BlendedBundle = super.bundle.copy(
    exportPackage = Seq(
      prjName,
      s"${prjName}.json",
      s"${prjName}.util",
      "blended.launcher.config"
    )
  )
}
