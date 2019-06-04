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

      Dependencies.scalatest % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.activeMqBroker % Test,
      Dependencies.activeMqKahadbStore % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.travesty % Test,
      Dependencies.asciiRender % Test
    )

    override def bundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.DispatcherActivator",
      exportPackage = Seq(
        projectName,
        s"${projectName}.cbe"
      )
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / parallelExecution := false,
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "DEBUG",
        "spec" -> "DEBUG",
        "blended" -> "DEBUG",
        "outbound" -> "DEBUG"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedStreams.project,
      BlendedJmsBridge.project,
      BlendedAkka.project,
      BlendedPersistence.project,

      BlendedPersistenceH2.project % Test,
      BlendedActivemqBrokerstarter.project % Test,
      BlendedStreamsTestsupport.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedTestsupport.project % Test
    )
  }
}
