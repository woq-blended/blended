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

      Dependencies.scalatest % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    ),
    adaptBundle = b => b.copy(
      exportPackage = Seq(
        s"${b.bundleSymbolicName}",
        s"${b.bundleSymbolicName}.jms",
        s"${b.bundleSymbolicName}.message",
        s"${b.bundleSymbolicName}.processor"
      )
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      Test / testlogLogPackages ++= Map("blended" -> "DEBUG")
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project,
    BlendedUtilLogging.project,
    BlendedJmsUtils.project,

    BlendedTestsupportPojosr.project % "test",
    BlendedTestsupport.project % "test"
  )
}
