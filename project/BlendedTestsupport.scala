import Dependencies._

object BlendedTestsupport extends ProjectSettings(
  prjName = "blended.testsupport",
  desc = "Some test helper classes.",
  osgi = false
) {

  override def libDependencies: Seq[sbt.ModuleID] = Seq(
    akkaActor,
    akkaTestkit,
    akkaCamel,
    scalatest,
    junit
  )

}
