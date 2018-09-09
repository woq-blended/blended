import sbt._

object BlendedPersistence extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.persistence",
    "Provide a technology agnostic persistence API with pluggable Data Objects defined in other bundles."
  ) {

    override val libDeps = Seq(
      Dependencies.slf4j,
      Dependencies.domino,
      Dependencies.scalatest % "test",
      Dependencies.mockitoAll % "test",
      Dependencies.slf4jLog4j12 % "test"
    )

  }

  override  val project  = helper.baseProject.dependsOn(
    BlendedAkka.project,
    BlendedTestsupport.project % "test"
  )
}
