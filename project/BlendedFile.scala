import blended.sbt.Dependencies
import de.wayofquality.sbt.filterresources.FilterResources
import de.wayofquality.sbt.filterresources.FilterResources.autoImport._
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import phoenix.ProjectFactory
import sbt._

object BlendedFile extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName : String = "blended.file"
    override val description : String = "Bundle to define a customizable Filedrop / Filepoll API"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.commonsIo % "test",
      Dependencies.activeMqBroker % "test",
      Dependencies.activeMqKahadbStore % "test",
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test",
      Dependencies.scalatest % "test"
    )

    override def plugins : Seq[AutoPlugin] = super.plugins ++ Seq(
      FilterResources
    )

    override def settings : Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "blended" -> "DEBUG"
      ),
      Test / filterProperties := Map(
        "project.build.testOutputDirectory" -> (Test / Keys.classDirectory).value.getAbsolutePath()
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedJmsUtils.project,
      BlendedStreams.project,

      BlendedTestsupport.project % Test
    )
  }
}
