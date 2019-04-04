import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedMgmtWs extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.mgmt.ws"
    override val description = "Web sockets interface for Mgmt clients."

    override def deps = Seq(
      Dependencies.akkaHttp,
      Dependencies.akkaHttpCore,

      Dependencies.akkaTestkit % Test,
      Dependencies.sttp % Test,
      Dependencies.sttpAkka % Test,
      Dependencies.scalatest % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.jclOverSlf4j % Test
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.MgmtWSActivator",
      exportPackage = Seq()
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedAkkaHttp.project,
      BlendedSecurityLoginApi.project,
      BlendedUpdaterConfigJvm.project,

      BlendedTestsupport.project % Test,
      BlendedMgmtRest.project % Test,
      BlendedUpdaterRemote.project % Test,
      BlendedPersistenceH2.project % Test,
      BlendedSecurityLoginImpl.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedSecurityLoginRest.project % Test
    )
  }
}
