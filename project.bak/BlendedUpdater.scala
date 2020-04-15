import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedUpdater extends ProjectFactory {
  //scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName = "blended.updater"
    override val description = "OSGi Updater"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.orgOsgi,
      Dependencies.domino,
      Dependencies.akkaOsgi,
      Dependencies.slf4j,
      Dependencies.typesafeConfig,
      Dependencies.akkaTestkit % Test,
      Dependencies.scalatest % Test,
      Dependencies.felixFramework % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.felixGogoRuntime % Test,
      Dependencies.felixGogoShell % Test,
      Dependencies.felixGogoCommand % Test,
      Dependencies.felixFileinstall % Test,
      Dependencies.mockitoAll % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.BlendedUpdaterActivator",
      importPackage = Seq(
        "blended.launcher.runtime;resolution:=optional"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUpdaterConfigJvm.project,
      BlendedLauncher.project,
      BlendedMgmtBase.project,
      BlendedContainerContextApi.project,
      BlendedAkka.project,
      BlendedTestsupport.project % Test
    )
  }
}
