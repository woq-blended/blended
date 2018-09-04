import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import Dependencies._
import sbt._

object BlendedSecurityJS extends JSProjectSettings() {
  override def libDependencies : Def.Initialize[Seq[librarymanagement.ModuleID]] = Def.setting(
    Seq(
      "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
      "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % "test"
    )
  )
}

object BlendedSecurityJVM extends ProjectSettings(
  prjName = "blended.security",
  desc = "Configuration bundle for the security framework."
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    prickle,
    scalatest % "test",
    logbackCore % "test",
    logbackClassic % "test"
  )

  override def bundle: BlendedBundle = super.bundle.copy(
    bundleActivator = "blended.security.internal.SecurityActivator",
    exportPackage = Seq(prjName, s"$prjName.json")
  )
}
