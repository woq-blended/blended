import Dependencies._

object BlendedUtil extends ProjectSettings(
  "blended.util",
  "Utility classes to use in other bundles."
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    akkaActor,
    akkaSlf4j,
    slf4j,
    akkaTestkit % "test",
    scalatest % "test",
    junit % "test",
    logbackClassic % "test",
    logbackCore % "test"
  )

  override def bundle: BlendedBundle = super.bundle.copy(
    exportPackage = Seq(prjName, s"$prjName.protocol", s"$prjName.config")
  )
}
