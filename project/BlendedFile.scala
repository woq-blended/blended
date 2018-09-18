import sbt._

import FilterResources.autoImport._

object BlendedFile extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.file",
    description = "Bundle to define a customizable Filedrop / Filepoll API",
    deps = Seq(
      Dependencies.akkaTestkit % "test",
      Dependencies.akkaSlf4j % "test",
      Dependencies.logbackClassic % "test",
      Dependencies.logbackCore % "test",
      Dependencies.scalatest % "test"
    ),
    adaptBundle = b => b.copy(
      exportPackage = Seq(b.bundleSymbolicName, s"${b.bundleSymbolicName}.protocol")
    )

  ) {

    override def extraPlugins: Seq[AutoPlugin] = super.extraPlugins ++ Seq(
      FilterResources
    )

    override def settings: Seq[sbt.Setting[_]] = super.settings ++ Seq(
      Test / filterProperties := Map(
        "project.build.testOutputDirectory" -> (Test / Keys.classDirectory).value.getAbsolutePath()
      )
    )
  }

  override val project = helper.baseProject.dependsOn(
    BlendedAkka.project,
    BlendedJmsUtils.project,
    BlendedTestsupport.project % "test"
  )
}