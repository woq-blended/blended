import sbt._

object BlendedMgmtWs extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.mgmt.ws",
    description = "Web sockets interface for Mgmt clients.",
    deps = Seq(
      Dependencies.akkaHttp,
      Dependencies.akkaHttpCore,

      Dependencies.akkaTestkit % "test",
      Dependencies.sttp % "test",
      Dependencies.sttpAkka % "test",
      Dependencies.scalatest % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test",
      Dependencies.jclOverSlf4j % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.MgmtWSActivator"
    )
  )

  override val project = helper.baseProject.dependsOn(
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
