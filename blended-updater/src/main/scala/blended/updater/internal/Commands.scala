package blended.updater.internal

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS
import scala.concurrent.Await
import scala.concurrent.duration.HOURS
import scala.concurrent.duration.MINUTES
import com.typesafe.config.ConfigFactory
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import blended.updater.Updater
import blended.updater.config.LocalRuntimeConfig
import blended.updater.config.RuntimeConfig
import blended.updater.config.OverlayConfig
import blended.updater.Updater.OperationSucceeded
import blended.updater.Updater.OperationFailed

class Commands(updater: ActorRef, env: Option[UpdateEnv])(implicit val actorSystem: ActorSystem) {

  val commandsWithDescription = Seq(
    "show" -> "Show all known profiles",
    "registerProfile" -> "Add a new profile",
    "registerOverlay" -> "Add a new overlay",
    "stage" -> "Stage a profile",
    "activate" -> "Activate a profile"
  )

  def show(): AnyRef = {
    implicit val timeout = Timeout(5, SECONDS)
    val configs = Await.result(
      ask(updater, Updater.GetRuntimeConfigs(UUID.randomUUID().toString())).mapTo[Updater.RuntimeConfigs],
      timeout.duration)

    def format(config: LocalRuntimeConfig): String = {
      val activeSuffix = env match {
        case Some(e) if e.launchedProfileName == config.runtimeConfig.name && e.launchedProfileVersion == config.runtimeConfig.version => " [active]"
        case _ => ""
      }
      s"${config.runtimeConfig.name}-${config.runtimeConfig.version}${activeSuffix}"
    }

    "staged: " + configs.staged.map(format).mkString(", ") + "\n" +
      "pending: " + configs.pending.map(format).mkString(", ") + "\n" +
      "invalid: " + configs.invalid.map(format).mkString(", ")
  }

  def registerProfile(file: File): AnyRef = {
    val config = ConfigFactory.parseFile(file).resolve()
    val runtimeConfig = RuntimeConfig.read(config).get
    println("About to add: " + runtimeConfig)

    implicit val timeout = Timeout(5, SECONDS)
    val reqId = UUID.randomUUID().toString()
    Await.result(
      ask(updater, Updater.AddRuntimeConfig(reqId, runtimeConfig)), timeout.duration) match {
        case OperationSucceeded(`reqId`) =>
          "Added: " + runtimeConfig
        case OperationFailed(`reqId`, error) =>
          "Failed: " + error
        case x =>
          "Error: " + x
      }
  }

  def stage(name: String, version: String): AnyRef = {
    implicit val timeout = Timeout(1, HOURS)
    val reqId = UUID.randomUUID().toString()
    Await.result(
        // TODO: support overlays
      ask(updater, Updater.StageProfile(reqId, name, version, overlays = Set())), timeout.duration) match {
        case OperationSucceeded(`reqId`) =>
          "Staged: " + name + " " + version
        case OperationFailed(`reqId`, reason) =>
          "Failed: " + reason
        case x =>
          "Error: " + x
      }
  }

  def activate(name: String, version: String): AnyRef = {
    env match {
      case Some(UpdateEnv(_, _, Some(lookupFile), _, _)) =>
        implicit val timeout = Timeout(5, MINUTES)
        val reqId = UUID.randomUUID().toString()
        Await.result(
          ask(updater, Updater.ActivateRuntimeConfig(reqId, name, version, Set())), timeout.duration) match {
            case OperationSucceeded(`reqId`) =>
              "Activated: " + name + " " + version
            case OperationFailed(`reqId`, reason) =>
              "Failed: " + reason
            case x =>
              "Error: " + x
          }
      case _ =>
        sys.error("No updateable environment detected. No profile lookup file defined.")
    }
  }

}