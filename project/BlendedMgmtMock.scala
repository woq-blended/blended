import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedMgmtMock extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.mgmt.mock"
    override val description = "Mock server to simulate a larger network of blended containers for UI testing."
    override val osgi = false

    override def deps = Seq(
      Dependencies.cmdOption,
      Dependencies.akkaActor,
      Dependencies.logbackClassic,
      Dependencies.logbackCore
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedMgmtBase.project,
      BlendedMgmtAgent.project,
      BlendedUtilLogging.project
    )
  }
}
