import sbt._

object BlendedAkkaHttp extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.akka.http",
    description = "Provide Akka HTTP support",
    deps = Seq(
      Dependencies.domino,
      Dependencies.orgOsgi,
      Dependencies.akkaStream,
      Dependencies.akkaOsgi,
      Dependencies.akkaHttp,
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.mockitoAll % "test",
      Dependencies.scalatest % "test",
      Dependencies.akkaHttpTestkit % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.BlendedAkkaHttpActivator"
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedContainerContextApi.project,
    BlendedDomino.project,
    BlendedUtil.project,
    BlendedUtilLogging.project,
    BlendedAkka.project,
    BlendedTestsupportPojosr.project % "test"
  )
}
