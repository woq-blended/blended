import sbt._
import blended.sbt.Dependencies

object BlendedContainerContextImpl extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.container.context.impl",
    description = "A simple OSGI service to provide access to the container's config directory",
    deps = Seq(
      Dependencies.orgOsgiCompendium,
      Dependencies.orgOsgi,
      Dependencies.domino,
      Dependencies.slf4j,
      Dependencies.julToSlf4j,
      Dependencies.springExpression,
      
      Dependencies.springCore % "test",
      Dependencies.scalatest % "test",
      Dependencies.scalacheck % "test",
      Dependencies.mockitoAll % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.jclOverSlf4j % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.ContainerContextActivator",
      importPackage = Seq("blended.launcher.runtime;resolution:=optional")
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedSecurityCrypto.project,
    BlendedContainerContextApi.project,
    BlendedUtilLogging.project,
    BlendedUtil.project,
    BlendedUpdaterConfigJvm.project,
    BlendedLauncher.project,

    BlendedTestsupport.project % "test"
  )
}
