object BlendedDomino
  extends ProjectSettings(
    prjName = "blended.domino",
    desc = "Blended Domino extension for new Capsule scopes.",
    libDeps = Seq(
      Dependencies.typesafeConfig,
      Dependencies.domino
    )
  )

