import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedContainerContextImpl extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.container.context.impl"
    override val description = "A simple OSGI service to provide access to the container's config directory"

    override def deps = Seq(
      Dependencies.orgOsgiCompendium,
      Dependencies.orgOsgi,
      Dependencies.domino,
      Dependencies.slf4j,
      Dependencies.julToSlf4j,
      Dependencies.springExpression,
      Dependencies.springCore,

      Dependencies.scalatest % "test",
      Dependencies.scalacheck % "test",
      Dependencies.mockitoAll % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.jclOverSlf4j % "test"
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.ContainerContextActivator",
      importPackage = Seq("blended.launcher.runtime;resolution:=optional")
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedSecurityCrypto.project,
      BlendedContainerContextApi.project,
      BlendedUtilLogging.project,
      BlendedUtil.project,
      BlendedUpdaterConfigJvm.project,
      BlendedLauncher.project,

      BlendedTestsupport.project % Test
    )
  }
}
