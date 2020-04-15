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
      Dependencies.commonsIo % Test,
      Dependencies.activeMqBroker % Test,
      Dependencies.activeMqKahadbStore % Test,
      Dependencies.akkaTestkit % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.scalatest % Test
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
