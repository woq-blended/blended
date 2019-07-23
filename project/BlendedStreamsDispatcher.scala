import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import sbt.Keys._
import sbt._
import blended.sbt.Dependencies

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
      Dependencies.logbackClassic % "test",
      Dependencies.travesty % "test",
      Dependencies.asciiRender % "test"
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.DispatcherActivator",
      exportPackage = Seq(
        b.bundleSymbolicName,
        b.bundleSymbolicName + ".cbe"
      )
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      Test / parallelExecution := false,
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "DEBUG",
        "spec" -> "DEBUG",
        "blended" -> "DEBUG",
        "outbound" -> "DEBUG",
        "blended.streams.transaction" -> "TRACE"
      )
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedStreams.project,
    BlendedJmsBridge.project,
    BlendedAkka.project,
    BlendedPersistence.project,

    BlendedPersistenceH2.project % "test",
    BlendedActivemqBrokerstarter.project % "test",
    BlendedStreamsTestsupport.project % "test",
    BlendedTestsupportPojosr.project % "test",
    BlendedTestsupport.project % "test"
  )
}
