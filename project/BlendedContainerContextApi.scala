object BlendedContainerContextApi
  extends ProjectSettings(
    prjName = "blended.container.context.api",
    desc = "The API for the Container Context and Identifier Services",
    libDeps = Seq(
      Dependencies.typesafeConfig,
      Dependencies.scalatest % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )
  )
