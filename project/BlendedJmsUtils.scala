import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import sbt._
import blended.sbt.Dependencies

object BlendedJmsUtils extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    "blended.jms.utils",
    "A bundle to provide a ConnectionFactory wrapper that monitors a single connection and is able to monitor the connection via an active ping.",
    deps = Seq(
      Dependencies.camelJms,
      Dependencies.jms11Spec,
      Dependencies.scalatest % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.akkaStream % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.logbackCore % "test",
      Dependencies.logbackClassic % "test"
    )
  ) {
    override def settings: Seq[sbt.Setting[_]] = defaultSettings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "App" -> "DEBUG",
        "spec" -> "DEBUG",
        "blended" -> "DEBUG"
      )
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedDomino.project,
    BlendedMgmtBase.project,
    BlendedContainerContextApi.project,
    BlendedUpdaterConfigJvm.project,
    BlendedUtilLogging.project,
    BlendedAkka.project,
    BlendedCamelUtils.project % "test",
    BlendedTestsupport.project % "test"
  )
}
