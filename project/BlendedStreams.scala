import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import phoenix.ProjectFactory
import sbt._

object BlendedStreams extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.streams"
    override val description : String = "Helper objects to work with Streams in blended integration flows."

    override def deps : Seq[ModuleID] = Seq(
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

    override def bundle : OsgiBundle = super.bundle.copy(
      exportPackage = Seq(
        projectName,
        s"$projectName.file",
        s"$projectName.jms",
        s"$projectName.message",
        s"$projectName.processor",
        s"$projectName.persistence",
        s"$projectName.transaction",
        s"$projectName.worklist"
      ),
      privatePackage = Seq(
        s"$projectName.internal",
        s"$projectName.transaction.internal",
        s"$projectName.jms.internal"
      )
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "App",
        "spec" -> "TRACE",
        "blended" -> "TRACE"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project,
      BlendedUtilLogging.project,
      BlendedJmsUtils.project,
      BlendedAkka.project,
      BlendedPersistence.project,

      BlendedActivemqBrokerstarter.project % Test,
      BlendedPersistenceH2.project % Test,
      BlendedTestsupportPojosr.project % Test,
      BlendedTestsupport.project % Test
    )
  }
}
