import blended.sbt.Dependencies
import phoenix.ProjectFactory
import sbt.librarymanagement.ModuleID

object BlendedUtilLogging extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  // scalastyle:on object.name
    override val projectName : String = "blended.util.logging"
    override val description : String = "Logging utility classes to use in other bundles"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.slf4j
    )

  }
}

