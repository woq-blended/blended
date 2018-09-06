object BlendedUtil extends ProjectSettings(
  "blended.util",
  "Utility classes to use in other bundles."
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    Dependencies.akkaActor,
    Dependencies.akkaSlf4j,
    Dependencies.slf4j,
    Dependencies.akkaTestkit % "test",
    Dependencies.scalatest % "test",
    Dependencies.junit % "test",
    Dependencies.logbackClassic % "test",
    Dependencies.logbackCore % "test"
  )

  override def bundle: BlendedBundle = super.bundle.copy(
    exportPackage = Seq(prjName, s"$prjName.protocol", s"$prjName.config")
  )
}
