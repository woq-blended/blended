import sbt._

object BlendedStreamsTestsupport extends ProjectFactory {

  private val settings = new ProjectSettings(
    projectName = "blended.streams.testsupport",
    description = "Some classes to make testing for streams a bit easier",
    osgi = false,
    deps = Seq(
      Dependencies.akkaTestkit,
      Dependencies.akkaActor,
      Dependencies.akkaStream
    )
  )

  override val project = settings.baseProject.dependsOn(
    BlendedUtilLogging.project
  )
}