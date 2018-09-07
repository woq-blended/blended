object BlendedTestsupport
  extends ProjectSettings(
    prjName = "blended.testsupport",
    desc = "Some test helper classes.",
    osgi = false,
    libDeps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaTestkit,
      Dependencies.akkaCamel,
      Dependencies.scalatest,
      Dependencies.junit
    )
  )
