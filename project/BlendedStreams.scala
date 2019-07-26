import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import phoenix.ProjectFactory
import sbt._

object BlendedStreams extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
    // scalastyle:on object.name
    override val projectName: String = "blended.streams"
    override val description: String = "Helper objects to work with Streams in blended integration flows."

    override def deps: Seq[ModuleID] = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.akkaPersistence,
      Dependencies.geronimoJms11Spec,
      Dependencies.levelDbJava,

      Dependencies.commonsIo % "test",
      Dependencies.scalacheck % "test",
      Dependencies.scalatest % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )

    override def bundle: OsgiBundle = super.bundle.copy(
      bundleActivator = s"${projectName}.internal.BlendedStreamsActivator",
      exportPackage = Seq(
        s"${projectName}",
        s"${projectName}.file",
        s"${projectName}.jms",
        s"${projectName}.json",
        s"${projectName}.message",
        s"${projectName}.processor",
        s"${projectName}.persistence",
        s"${projectName}.transaction",
        s"${projectName}.worklist"
      ),
      privatePackage = Seq(s"${projectName}.internal", s"${projectName}.transaction.internal")
    )

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "DEBUG",
        "spec" -> "DEBUG",
        "blended" -> "DEBUG",
        "blended.streams.transaction" -> "TRACE",
        "spec.flow.stream" -> "TRACE"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedUtilLogging.project,
      BlendedJmsUtils.project,
      BlendedAkka.project,
      BlendedPersistence.project,

      BlendedActivemqBrokerstarter.project % "test",
      BlendedPersistenceH2.project % "test",
      BlendedTestsupportPojosr.project % "test",
      BlendedTestsupport.project % "test"
    )
  }
}
