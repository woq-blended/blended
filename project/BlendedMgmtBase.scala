import sbt._

object BlendedMgmtBase extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.mgmt.base",
    description = "Shared classes for management and reporting facility.",
    deps = Seq(
      Dependencies.scalatest % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.MgmtBaseActivator"
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedContainerContextApi.project,
    BlendedUtil.project,
    BlendedUtilLogging.project
  )
}
