import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedMgmtMock extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.mgmt.mock"
    override val description : String = "Mock server to simulate a larger network of blended containers for UI testing."
    override val osgi = false

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.cmdOption,
      Dependencies.akkaActor,
      Dependencies.logbackClassic,
      Dependencies.logbackCore
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedMgmtBase.project,
      BlendedMgmtAgent.project,
      BlendedContainerContextImpl.project,
      BlendedUtilLogging.project
    )
  }
}
