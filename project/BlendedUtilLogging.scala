import sbt._

object BlendedUtilLogging extends ProjectSettings(
  "blended.util.logging",
  "Logging utility classes to use in other bundles."
) {

  override val libDependencies : Seq[ModuleID] = Seq(
    Dependencies.slf4j
  )
}
