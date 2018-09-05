import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import Dependencies._
import sbt._
import sbt.Keys._

object BlendedUpdaterConfigJS extends JSProjectSettings() {
  override def libDependencies : Def.Initialize[Seq[librarymanagement.ModuleID]] = Def.setting(
    Seq(
      "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
      "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % "test"
    )
  )
}

object BlendedUpdaterConfigJVM extends ProjectSettings(
  prjName = "blended.updater.config",
  desc = "Configurations for Updater and Launcher"
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    prickle,
    typesafeConfig,
    scalatest % "test",
    logbackClassic % "test",
    logbackCore % "test"
  )

  override def bundle: BlendedBundle = super.bundle.copy(
    exportPackage = Seq(prjName, s"$prjName.json", s"$prjName.util", "blended.launcher.config")
  )
}