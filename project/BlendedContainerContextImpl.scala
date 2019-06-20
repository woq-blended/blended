import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedContainerContextImpl extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName : String = "blended.container.context.impl"
    override val description : String = "A simple OSGI service to provide access to the container's config directory"

    override def deps : Seq[ModuleID] = Seq(
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

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.ContainerContextActivator",
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
