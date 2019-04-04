import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt._

object BlendedUtil extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.util"
    override val description = "Utility classes to use in other bundles"

    override def deps = Seq(
      Dependencies.akkaActor,
      Dependencies.akkaSlf4j,
      Dependencies.slf4j,
      Dependencies.akkaTestkit % Test,
      Dependencies.scalatest % Test,
      Dependencies.junit % Test,
      Dependencies.logbackClassic % Test,
      Dependencies.logbackCore % Test
    )

    override def bundle: BlendedBundle = super.bundle.copy(
      exportPackage = Seq(
        projectName,
        s"${projectName}.config"
      )
    )
  }
}

