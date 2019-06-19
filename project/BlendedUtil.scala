import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedUtil extends ProjectFactory {
  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName = "blended.util"
    override val description = "Utility classes to use in other bundles"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaSlf4j,
      Dependencies.slf4j,
      Dependencies.akkaTestkit % Test,
      Dependencies.scalatest % Test,
      Dependencies.junit % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      exportPackage = Seq(
        projectName,
        s"$projectName.config"
      )
    )
  }
}

