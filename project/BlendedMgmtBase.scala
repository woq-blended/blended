import sbt._

object BlendedMgmtBase extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.mgmt.base",
    "Shared classes for management and reporting facility."
  ) {

    override val libDeps = Seq(
      Dependencies.scalatest % "test"
    )

    override lazy val bundle: BlendedBundle = defaultBundle.copy(
      bundleActivator = s"${prjName}.internal.MgmtBaseActivator"
    )
  }

  override  val project  = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedContainerContextApi.project,
    BlendedUtil.project,
    BlendedUtilLogging.project
  )
}
