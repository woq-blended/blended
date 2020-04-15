import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedUpdaterRemote extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name

    override val projectName = "blended.updater.remote"
    override val description = "OSGi Updater remote handle support"

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
      Dependencies.mockitoAll % Test,
      Dependencies.springCore % Test,
      Dependencies.springBeans % Test,
      Dependencies.springContext % Test,
      Dependencies.springExpression % Test,
      Dependencies.commonsLogging % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      bundleActivator = s"$projectName.internal.RemoteUpdaterActivator"
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedPersistence.project,
      BlendedUpdaterConfigJvm.project,
      BlendedMgmtBase.project,
      BlendedLauncher.project,
      BlendedContainerContextApi.project,
      BlendedAkka.project,
      BlendedTestsupport.project % Test,
      BlendedPersistenceH2.project % Test
    )
  }
}
