import sbt._

object BlendedDomino extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.domino",
    description = "Blended Domino extension for new Capsule scopes.",
    deps = Seq(
      Dependencies.typesafeConfig,
      Dependencies.domino
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedContainerContextApi.project
  )

}
