import sbt._

object BlendedCamelUtils extends ProjectHelper {

  private[this] val helper = new ProjectSettings(
    "blended.camel.utils",
    "Useful helpers for Camel"
  ) {

    override val libDeps = Seq(
      Dependencies.orgOsgi,
      Dependencies.orgOsgiCompendium,
      Dependencies.camelJms,
      Dependencies.slf4j
    )
  }

  override  val project  = helper.baseProject.dependsOn(
    BlendedAkka.project
  )
}
