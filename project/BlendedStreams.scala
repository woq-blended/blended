import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import sbt._
import blended.sbt.Dependencies

object BlendedStreams extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.streams",
    description = "Helper objects to work with Streams in blended integration flows.",
    deps = Seq(
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
    ),
    adaptBundle = b => b.copy(
      bundleActivator = s"${b.bundleSymbolicName}.internal.BlendedStreamsActivator",
      exportPackage = Seq(
        s"${b.bundleSymbolicName}",
        s"${b.bundleSymbolicName}.file",
        s"${b.bundleSymbolicName}.jms",
        s"${b.bundleSymbolicName}.json",
        s"${b.bundleSymbolicName}.message",
        s"${b.bundleSymbolicName}.processor",
        s"${b.bundleSymbolicName}.persistence",
        s"${b.bundleSymbolicName}.transaction",
        s"${b.bundleSymbolicName}.worklist"
      ),
      privatePackage = b.privatePackage ++ Seq(s"${b.bundleSymbolicName}.transaction.internal")
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "DEBUG",
        "spec" -> "DEBUG",
        "blended" -> "DEBUG",
        "blended.streams.transaction" -> "TRACE",
        "spec.flow.stream" -> "TRACE"
      )
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedUtilLogging.project,
    BlendedJmsUtils.project,
    BlendedAkka.project,

    BlendedActivemqBrokerstarter.project % "test",
    BlendedTestsupportPojosr.project % "test",
    BlendedTestsupport.project % "test"
  )
}
