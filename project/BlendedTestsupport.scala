object BlendedTestsupport extends ProjectSettings(
  prjName = "blended.testsupport",
  desc = "Some test helper classes.",
  osgi = false
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    Dependencies.akkaActor,
    Dependencies.akkaTestkit,
    Dependencies.akkaCamel,
    Dependencies.scalatest,
    Dependencies.junit
  )

}
