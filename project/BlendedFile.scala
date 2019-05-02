import de.wayofquality.sbt.filterresources.FilterResources
import de.wayofquality.sbt.filterresources.FilterResources.autoImport._
import sbt._
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import blended.sbt.Dependencies

object BlendedFile extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.file",
    description = "Bundle to define a customizable Filedrop / Filepoll API",
    deps = Seq(
      Dependencies.commonsIo % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test",
      Dependencies.scalatest % "test"
    )
  ) {

    override def extraPlugins: Seq[AutoPlugin] = super.extraPlugins ++ Seq(
      FilterResources
    )

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "blended" -> "INFO"
      ),
      Test / filterProperties := Map(
        "project.build.testOutputDirectory" -> (Test / Keys.classDirectory).value.getAbsolutePath()
      )
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedAkka.project,
    BlendedJmsUtils.project,
    BlendedStreams.project,

    BlendedTestsupport.project % "test"
  )
}
