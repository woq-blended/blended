object BlendedUtil extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.util",
    description = "Utility classes to use in other bundles",
    deps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaSlf4j,
      Dependencies.slf4j,
      Dependencies.akkaTestkit % "test",
      Dependencies.scalatest % "test",
      Dependencies.junit % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test"
    ),
    adaptBundle = b => b.copy(
      exportPackage = Seq(b.bundleSymbolicName, s"${b.bundleSymbolicName}.protocol", s"${b.bundleSymbolicName}.config")
    )
  )

  override val project = helper.baseProject
}

