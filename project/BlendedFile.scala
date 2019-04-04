import de.wayofquality.sbt.filterresources.FilterResources
import de.wayofquality.sbt.filterresources.FilterResources.autoImport._
import sbt._
import de.wayofquality.sbt.testlogconfig.TestLogConfig.autoImport._
import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedFile extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.file"
    override val description = "Bundle to define a customizable Filedrop / Filepoll API"

    override def deps = Seq(
      Dependencies.akkaTestkit % Test,
      Dependencies.akkaSlf4j % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test,
      Dependencies.scalatest % Test
    )

    override def plugins: Seq[AutoPlugin] = super.plugins ++ Seq(
      FilterResources
    )

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / testlogDefaultLevel := "INFO",
      Test / testlogLogPackages ++= Map(
        "blended" -> "TRACE"
      ),
      Test / filterProperties := Map(
        "project.build.testOutputDirectory" -> (Test / Keys.classDirectory).value.getAbsolutePath()
      )
    )

    override def dependsOn: Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project,
      BlendedJmsUtils.project,
      BlendedStreams.project,
      BlendedTestsupport.project % Test
    )
  }
}
