import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedMgmtAgent extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.mgmt.agent"
    override val description = "Bundle to regularly report monitoring information to a central container hosting the container registry"

    override def deps = Seq(
      Dependencies.orgOsgi,
      Dependencies.akkaOsgi,
      Dependencies.akkaHttp,
      Dependencies.akkaStream,
      Dependencies.akkaTestkit % Test,
      Dependencies.scalatest % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.AgentActivator",
      exportPackage = Seq()
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedUpdaterConfigJvm.project,
      BlendedUtilLogging.project,
      BlendedPrickleAkkaHttp.project
    )
  }
}
