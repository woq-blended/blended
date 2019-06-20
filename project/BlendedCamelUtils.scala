import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedCamelUtils extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName : String = "blended.camel.utils"
    override val description : String = "Useful helpers for Camel"

    override def deps : Seq[ModuleID] = Seq(
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
