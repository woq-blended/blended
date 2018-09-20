import sbt._

object BlendedCamelUtils extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.camel.utils",
    description = "Useful helpers for Camel",
    deps = Seq(
      Dependencies.orgOsgi,
      Dependencies.orgOsgiCompendium,
      Dependencies.camelJms,
      Dependencies.slf4j
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedAkka.project
  )
}
