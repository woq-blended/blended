import sbt._

object BlendedDomino extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.domino",
    "Blended Domino extension for new Capsule scopes."
  ) {
    override val libDeps = Seq(
      Dependencies.typesafeConfig,
      Dependencies.domino
    )
  }

  override  val project  = helper.baseProject.dependsOn(BlendedContainerContextApi.project)
}
