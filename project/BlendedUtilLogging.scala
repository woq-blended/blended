import blended.sbt.Dependencies
import phoenix.ProjectFactory

object BlendedUtilLogging extends ProjectFactory {
  object config extends ProjectSettings {
    override val projectName = "blended.util.logging"
    override val description = "Logging utility classes to use in other bundles"

    override def deps = Seq(
      Dependencies.slf4j
    )

  }
}

