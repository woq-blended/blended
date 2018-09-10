import sbt._

object BlendedContainerContextApi extends ProjectHelper {

  private[this] val helper : ProjectSettings = new ProjectSettings(
    "blended.container.context.api",
    "The API for the Container Context and Identifier Services"
  ) {


    override def libDeps = Seq(
      Dependencies.typesafeConfig,
      Dependencies.scalatest      % "test",
      Dependencies.logbackCore    % "test",
      Dependencies.logbackClassic % "test"
    )
  }

  override val project  = helper.baseProject.dependsOn(BlendedUtilLogging.project)
}
