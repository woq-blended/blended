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

      Dependencies.akkaTestkit % "test",
      Dependencies.sttp % "test",
      Dependencies.sttpAkka % "test",
      Dependencies.scalatest % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test",
      Dependencies.jclOverSlf4j % "test"
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

      BlendedTestsupport.project % "test",
      BlendedMgmtRest.project % "test",
      BlendedUpdaterRemote.project % "test",
      BlendedPersistenceH2.project % "test",
      BlendedSecurityLoginImpl.project % "test",
      BlendedTestsupportPojosr.project % "test",
      BlendedSecurityLoginRest.project % "test"
    )
  }
}
