import blended.sbt.Dependencies
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import phoenix.ProjectFactory
import sbt._

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

      Dependencies.commonsIo % Test,
      Dependencies.scalacheck % Test,
      Dependencies.scalatest % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.activeMqBroker % Test,
      Dependencies.activeMqKahadbStore % Test,
      Dependencies.logbackCore % Test,
      Dependencies.logbackClassic % Test
    )

    override def bundle = super.bundle.copy(
      exportPackage = Seq(
        projectName,
        s"${projectName}.file",
        s"${projectName}.jms",
        s"${projectName}.message",
        s"${projectName}.processor",
        s"${projectName}.persistence",
        s"${projectName}.transaction",
        s"${projectName}.worklist"
      )
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "DEBUG",
        "spec" -> "DEBUG",
        "blended" -> "DEBUG"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedUtilLogging.project,
      BlendedJmsUtils.project,
      BlendedAkka.project,
      BlendedPersistence.project,

      BlendedActivemqBrokerstarter.project % "test",
      BlendedPersistenceH2.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedTestsupport.project % Test
    )
  }
}
