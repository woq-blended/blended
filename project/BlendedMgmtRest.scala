import sbt._

object BlendedMgmtRest extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.mgmt.rest",
    description = "REST interface to accept POST's from distributed containers. These will be delegated to the container registry.",
    deps = Seq(
      Dependencies.akkaActor,
      Dependencies.domino,
      Dependencies.akkaHttp,
      Dependencies.akkaHttpCore,
      Dependencies.akkaStream,

      Dependencies.scalatest % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.sttp % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.lambdaTest % "test",
      Dependencies.jclOverSlf4j % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.MgmtRestActivator"
    )
  )
  override  val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedAkkaHttp.project,
    BlendedUpdaterRemote.project,
    BlendedSecurityAkkaHttp.project,
    BlendedAkka.project,
    BlendedPrickleAkkaHttp.project,
    BlendedMgmtRepo.project,

    BlendedTestsupportPojosr.project % "test",
    BlendedPersistenceH2.project % "test"
  )
}
