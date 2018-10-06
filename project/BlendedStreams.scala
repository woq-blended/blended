import sbt._
import TestLogConfig.autoImport._

object BlendedStreams extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.streams",
    description = "Helper objects to work with Streams in blended integration flows.",
    deps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaStream,
      Dependencies.geronimoJms11Spec,

      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      exportPackage = Seq(s"${b.bundleSymbolicName}", s"${b.bundleSymbolicName}.jms", s"${b.bundleSymbolicName}.message")
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      Test / testlogLogToFile := false, 
      Test / testlogLogToConsole := true,
      Test / testlogLogPackages ++= Map("blended" -> "DEBUG")
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedJmsUtils.project
  )
}
