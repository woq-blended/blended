object BlendedPersistence extends ProjectSettings(
  "blended.persistence",
  "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles."
) {

  override val libDependencies: Seq[sbt.ModuleID] = Seq(
    Dependencies.slf4j,
    Dependencies.domino,
    Dependencies.scalatest % "test",
    Dependencies.mockitoAll % "test",
    Dependencies.slf4jLog4j12 % "test"
  )
}
