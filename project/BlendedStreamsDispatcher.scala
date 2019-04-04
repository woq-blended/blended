import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import sbt.Keys._
import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedStreamsDispatcher extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.streams.dispatcher"
    override val description = "A generic Dispatcher to support common integration routing."

    override def deps = Seq(
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
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.DispatcherActivator",
      exportPackage = Seq(
        projectName,
        s"${projectName}.cbe"
      )
    )

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / parallelExecution := false,
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "TRACE",
        "spec" -> "TRACE",
        "blended" -> "TRACE"
      )
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
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
}
