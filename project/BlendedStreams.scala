import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import sbt._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedStreams extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.streams"
    override val description = "Helper objects to work with Streams in blended integration flows."

    override def deps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.akkaPersistence,
      Dependencies.geronimoJms11Spec,
      Dependencies.levelDbJava,

      Dependencies.scalacheck % "test",
      Dependencies.scalatest % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      exportPackage = Seq(
        projectName,
        s"${projectName}.jms",
        s"${projectName}.message",
        s"${projectName}.processor",
        s"${projectName}.persistence",
        s"${projectName}.transaction",
        s"${projectName}.worklist"
      )
    )

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "TRACE",
        "spec" -> "TRACE",
        "blended" -> "TRACE"
      )
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedUtilLogging.project,
      BlendedJmsUtils.project,
      BlendedAkka.project,
      BlendedPersistence.project,

      BlendedPersistenceH2.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedTestsupport.project % Test
    )
  }
}
