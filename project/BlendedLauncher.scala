import sbt._
import Dependencies._

object BlendedLauncher extends ProjectSettings(
  prjName = "blended.launcher",
  desc = "Provide an OSGi Launcher"
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    cmdOption,
    orgOsgi,
    typesafeConfig,
    logbackCore,
    logbackClassic

  )
}
