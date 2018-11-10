import TestLogConfig.autoImport._
import sbt._
import sbt.Keys._

object BlendedStreamsDispatcher extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.streams.dispatcher",
    description = "A generic Dispatcher to support common integration routing.",
    deps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.geronimoJms11Spec,
      Dependencies.akkaPersistence,
      Dependencies.levelDbJava,

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
      Test / parallelExecution := false,
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "blended" -> "TRACE"
      )
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedStreams.project,
    BlendedJmsBridge.project,
    BlendedAkka.project,

    BlendedActivemqBrokerstarter.project % "test",
    BlendedStreamsTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test",
    BlendedTestsupport.project % "test"
  )
}
