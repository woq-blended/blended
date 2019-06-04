import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedCamelUtils extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.camel.utils"
    override val description = "Useful helpers for Camel"

    override def deps = Seq(
      Dependencies.orgOsgi,
      Dependencies.orgOsgiCompendium,
      Dependencies.camelJms,
      Dependencies.slf4j
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedAkka.project
    )
  }
}
