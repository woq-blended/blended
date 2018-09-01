object BlendedDomino extends ProjectSettings(
  "blended.domnino",
  "Blended Domino extension for new Capsule scopes."
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    Dependencies.typesafeConfig,
    Dependencies.domino
  )
}

