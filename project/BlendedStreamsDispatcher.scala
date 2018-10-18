import sbt._
import TestLogConfig.autoImport._

object BlendedStreamsDispatcher extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.streams.dispatcher",
    description = "A generic Dispatcher to support common integration routing.",
    deps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.geronimoJms11Spec,

      Dependencies.scalatest % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      exportPackage = Seq(s"${b.bundleSymbolicName}")
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      Test / testlogLogPackages ++= Map(
        "blended" -> "DEBUG",
        "blended.container.context.api" -> "INFO"
      )
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedStreams.project,
    BlendedJmsBridge.project,

    BlendedStreamsTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test",
    BlendedTestsupport.project % "test"
  )
}
