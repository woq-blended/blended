object BlendedPersistence
  extends ProjectSettings(
    prjName = "blended.persistence",
    desc = "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles.",
    libDeps = Seq(
      Dependencies.slf4j,
      Dependencies.domino,
      Dependencies.scalatest % "test",
      Dependencies.mockitoAll % "test",
      Dependencies.slf4jLog4j12 % "test"
    )
  ) 