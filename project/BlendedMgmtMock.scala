import sbt._

object BlendedMgmtMock extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.mgmt.mock",
    description = "Mock server to simulate a larger network of blended containers for UI testing.",
    osgi = false,
    deps = Seq(
      Dependencies.cmdOption,
      Dependencies.akkaActor,
      Dependencies.wiremockStandalone,
      Dependencies.logbackClassic,
      Dependencies.logbackCore
    )
  )
  override  val project = helper.baseProject.dependsOn(
    BlendedMgmtBase.project,
    BlendedMgmtAgent.project,
    BlendedUtilLogging.project
  )
}
