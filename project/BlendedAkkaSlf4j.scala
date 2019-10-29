import blended.sbt.Dependencies
import sbt._

object BlendedAkkaLogging extends ProjectFactory {

  private[this] val helper = new ProjectSettings(
    projectName = "blended.akka.logging",
    description = "Redirect Akka Logging to the Blended logging framework",
    deps = Seq(
      Dependencies.akkaActor
    ),
    adaptBundle = b => b.copy(
      privatePackage = Seq( "blended.akka.logging" ),
      additionalHeaders = Map(
        "Fragment-Host" -> "com.typesafe.akka.actor"
      )
    )
  )

  override val project = helper.baseProject.dependsOn(
    BlendedUtilLogging.project
  )
}
