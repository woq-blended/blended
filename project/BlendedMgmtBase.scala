import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedMgmtBase extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.mgmt.base"
    override val description = "Shared classes for management and reporting facility."

    override def deps = Seq(
      Dependencies.scalatest % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.MgmtBaseActivator"
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedDomino.project,
      BlendedContainerContextApi.project,
      BlendedUtil.project,
      BlendedUtilLogging.project
    )
  }
}
