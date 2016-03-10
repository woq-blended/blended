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
import com.typesafe.config.ConfigParseOptions
import blended.updater.config.OverlayRef
import scala.annotation.varargs

class Commands(updater: ActorRef, env: Option[UpdateEnv])(implicit val actorSystem: ActorSystem) {

  val commandsWithDescription = Seq(
    "showProfiles" -> "Show all (staged) profiles",
    "showRuntimeConfigs" -> "Show all known runtime configs",
    "showOverlays" -> "Show all known overlays",
    "registerProfile" -> "Add a new profile",
    "registerOverlay" -> "Add a new overlay",
    "stageProfile" -> "Stage a profile",
    "activateProfile" -> "Activate a profile"
  )

  def showProfiles(): AnyRef = {
    implicit val timeout = Timeout(5, SECONDS)
    val configs = Await.result(
      ask(updater, Updater.GetProfileIds(UUID.randomUUID().toString())).mapTo[Updater.Result[Set[_]]],
      timeout.duration).result

    s"${configs.size} profiles:\n${configs.mkString("\n")}"
  }

  def showRuntimeConfigs(): AnyRef = {
    implicit val timeout = Timeout(5, SECONDS)
    val configs = Await.result(
      ask(updater, Updater.GetRuntimeConfigs(UUID.randomUUID().toString())).mapTo[Updater.Result[Set[_]]],
      timeout.duration).result

    s"${configs.size} runtime configs:\n${
      configs.toList.map {
        case LocalRuntimeConfig(c, _) => s"${c.runtimeConfig.name}-${c.runtimeConfig.version}"
      }.sorted.mkString("\n")
    }"
  }

  def showOverlays(): AnyRef = {
    implicit val timeout = Timeout(5, SECONDS)
    val configs = Await.result(
      ask(updater, Updater.GetOverlays(UUID.randomUUID().toString())).mapTo[Updater.Result[Set[_]]],
      timeout.duration).result

    s"${configs.size} profiles:\n${configs.mkString("\n")}"
  }

  def registerRuntimeConfig(file: File): AnyRef = {
    val config = ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
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

  def registerOverlay(file: File): AnyRef = {
    val config = ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
    val overlayConfig = OverlayConfig.read(config).get
    println("About to add: " + overlayConfig)

    implicit val timeout = Timeout(5, SECONDS)
    val reqId = UUID.randomUUID().toString()
    Await.result(
      ask(updater, Updater.AddOverlayConfig(reqId, overlayConfig)), timeout.duration) match {
        case OperationSucceeded(`reqId`) =>
          "Added: " + overlayConfig
        case OperationFailed(`reqId`, error) =>
          "Failed: " + error
        case x =>
          "Error: " + x
      }
  }

  @varargs
  def stageProfile(rcName: String, rcVersion: String, overlayNameVersion: String*): AnyRef = {
    if (overlayNameVersion.size % 2 != 0) {
      sys.error(s"Missing version for overlay ${overlayNameVersion.last}")
    }
    val overlays = overlayNameVersion.sliding(2, 2).map(o => OverlayRef(o(0), o(1))).toSet
    val overlaysAsString =
      if (overlays.isEmpty) ""
      else overlays.toList.sorted.map(o => s"${o.name}-${o.version}").mkString(" with ", " with ", "")

    val asString = s"${rcName}-${rcVersion}${overlaysAsString}"

    implicit val timeout = Timeout(1, HOURS)
    val reqId = UUID.randomUUID().toString()
    Await.result(
      // TODO: support overlays
      ask(updater, Updater.StageProfile(reqId, rcName, rcVersion, overlays)), timeout.duration) match {
        case OperationSucceeded(`reqId`) =>
          "Staged: " + asString
        case OperationFailed(`reqId`, reason) =>
          "Stage failed: " + asString + "\nReason: " + reason
        case x =>
          "Stage failed: " + asString + "\nError: " + x
      }
  }

  def activateProfile(name: String, version: String): AnyRef = {
    env match {
      case Some(UpdateEnv(_, _, Some(lookupFile), _, _, _)) =>
        implicit val timeout = Timeout(5, MINUTES)
        val reqId = UUID.randomUUID().toString()
        Await.result(
          ask(updater, Updater.ActivateProfile(reqId, name, version, Set())), timeout.duration) match {
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