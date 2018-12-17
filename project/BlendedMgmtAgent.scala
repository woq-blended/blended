import sbt._
import blended.sbt.Dependencies

object BlendedMgmtAgent extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.mgmt.agent",
    description = "Bundle to regularly report monitoring information to a central container hosting the container registry",
    deps = Seq(
      Dependencies.orgOsgi,
      Dependencies.akkaOsgi,
      Dependencies.akkaHttp,
      Dependencies.akkaStream,
      Dependencies.akkaTestkit % "test",
      Dependencies.scalatest % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.AgentActivator",
      exportPackage = Seq()
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedAkka.project,
    BlendedUpdaterConfigJvm.project,
    BlendedUtilLogging.project,
    BlendedPrickleAkkaHttp.project
  )
}
