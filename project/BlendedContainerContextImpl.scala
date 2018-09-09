import sbt._

object BlendedContainerContextImpl extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.container.context.impl",
    "A simple OSGI service to provide access to the container's config directory"
  ) {

    override val libDeps = Seq(
      Dependencies.orgOsgiCompendium,
      Dependencies.orgOsgi,
      Dependencies.domino,
      Dependencies.slf4j,
      Dependencies.julToSlf4j,
      Dependencies.scalatest % "test",
      Dependencies.mockitoAll % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )

    override lazy val bundle: BlendedBundle = defaultBundle.copy(
      bundleActivator = s"${prjName}.internal.ContainerContextActivator",
      importPackage = Seq("blended.launcher.runtime;resolution:=optional")
    )
  }

  override  val project  = helper.baseProject.dependsOn(
    BlendedContainerContextApi.project,
    BlendedUtilLogging.project,
    BlendedUtil.project,
    BlendedUpdaterConfigJvm.project,
    BlendedLauncher.project
  )
}
