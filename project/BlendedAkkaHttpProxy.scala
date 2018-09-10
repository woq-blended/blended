import sbt._

object BlendedAkkaHttpProxy extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.akka.http.proxy",
    description = "Provide Akka HTTP Proxy support",
    deps = Seq(
      Dependencies.domino,
      Dependencies.akkaStream,
      Dependencies.akkaHttp,
      Dependencies.akkaActor,
      Dependencies.akkaSlf4j % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.scalatest % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedContainerContextApi.project,
    BlendedAkka.project,
    BlendedAkkaHttp.project,
    BlendedUtil.project,
    BlendedUtilLogging.project,
    BlendedTestsupportPojosr.project % "test"
  )
}
