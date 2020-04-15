import blended.sbt.Dependencies
import blended.sbt.phoenix.osgi.OsgiBundle
import phoenix.ProjectFactory
import sbt._

object BlendedAkkaLogging extends ProjectFactory {

  // scalastyle:off object.name
  object config extends ProjectSettings {
  //scalastyle:on object.name
    override val projectName = "blended.akka.logging"
    override val description = "Redirect Akka Logging to the Blended logging framework"

    override def deps : Seq[ModuleID] = Seq(
      Dependencies.akkaActor
    )

    override def bundle : OsgiBundle = super.bundle.copy(
      privatePackage = Seq( "blended.akka.logging" ),
      additionalHeaders = Map(
        "Fragment-Host" -> "com.typesafe.akka.actor"
      )
    )

    override def dependsOn : Seq[ClasspathDep[ProjectReference]] = Seq(
      BlendedUtilLogging.project
    )
  }
}
