import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object BlendedSecurityJs extends JsProjectSettings() {
  override def libDependencies: Def.Initialize[Seq[librarymanagement.ModuleID]] = Def.setting(
    Seq(
      "com.github.benhutchison" %%% "prickle" % Dependencies.prickleVersion,
      "org.scalatest" %%% "scalatest" % Dependencies.scalatestVersion % "test"
    )
  )
}

object BlendedSecurityJvm
  extends ProjectSettings(
    prjName = "blended.security",
    desc = "Configuration bundle for the security framework.",
    libDeps = Seq(
      Dependencies.prickle,
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    customProjectFactory = true
  ) {

  override def bundle: BlendedBundle = super.bundle.copy(
    bundleActivator = "blended.security.internal.SecurityActivator",
    exportPackage = Seq(prjName, s"$prjName.json")
  )

}
