package blended.updater.internal

import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit.SECONDS

import scala.concurrent.Await
import scala.concurrent.duration.{HOURS, MINUTES}

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import blended.updater.Updater.{OperationFailed, OperationSucceeded}
import blended.updater.config._
import blended.updater.{LocalProfile, ProfileId, Updater}
import blended.util.logging.Logger
import com.typesafe.config.{ConfigFactory, ConfigParseOptions}

class Commands(updater: ActorRef, env: Option[UpdateEnv])(implicit val actorSystem: ActorSystem) {

  private[this] val log = Logger[Commands]

  val commandsWithDescription = Seq(
    "showProfiles" -> "Show all (staged) profiles",
    "showRuntimeConfigs" -> "Show all known runtime configs",
    "registerRuntimeConfig" -> "Add a new runtime config",
    "registerProfile" -> "Add a new profile",
    "stageProfile" -> "Stage a profile",
    "activateProfile" -> "Activate a profile"
  )

  def showProfiles(): AnyRef = {
    implicit val timeout = Timeout(5, SECONDS)
    val activeProfile = env.map(env => ProfileId(env.launchedProfileName, env.launchedProfileVersion))
    log.debug(s"acitive profile: ${activeProfile}")

    val profiles = Await
      .result(
        ask(updater, Updater.GetProfiles(UUID.randomUUID().toString())).mapTo[Updater.Result[Set[_]]],
        timeout.duration
      )
      .result

    s"${profiles.size} profiles:\n${profiles
      .map {
        case p: LocalProfile =>
          val activePart = if (activeProfile.exists(_ == p.profileId)) " (active)" else ""
          p.profileId.toString() + ": " + p.state + activePart
      }
      .mkString("\n")}"
  }

  def showRuntimeConfigs(): AnyRef = {
    implicit val timeout = Timeout(5, SECONDS)
    val configs = Await
      .result(
        ask(updater, Updater.GetRuntimeConfigs(UUID.randomUUID().toString())).mapTo[Updater.Result[Set[_]]],
        timeout.duration
      )
      .result

    s"${configs.size} runtime configs:\n${configs.toList
      .map {
        case LocalRuntimeConfig(c, _) => s"${c.profile.name}-${c.profile.version}"
      }
      .sorted
      .mkString("\n")}"
  }

  def registerProfile(file: File): AnyRef = registerRuntimeConfig(file)

  def registerRuntimeConfig(file: File): AnyRef = {
    val config = ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setAllowMissing(false)).resolve()
    val runtimeConfig = ProfileCompanion.read(config).get
    println("About to add: " + runtimeConfig)

    implicit val timeout = Timeout(5, SECONDS)
    val reqId = UUID.randomUUID().toString()
    Await.result(
      ask(updater, Updater.AddRuntimeConfig(reqId, runtimeConfig)),
      timeout.duration
    ) match {
      case OperationSucceeded(`reqId`) =>
        "Added: " + runtimeConfig
      case OperationFailed(`reqId`, error) =>
        "Failed: " + error
      case x =>
        "Error: " + x
    }
  }

  def stageProfile(rcName: String, rcVersion: String): AnyRef = {

    val asString = s"${rcName}-${rcVersion}"

    implicit val timeout = Timeout(1, HOURS)
    val reqId = UUID.randomUUID().toString()
    Await.result(
      ask(updater, Updater.StageProfile(reqId, rcName, rcVersion)),
      timeout.duration
    ) match {
      case OperationSucceeded(`reqId`) =>
        "Staged: " + asString
      case OperationFailed(`reqId`, reason) =>
        "Stage failed: " + asString + "\nReason: " + reason
      case x =>
        "Stage failed: " + asString + "\nError: " + x
    }
  }

  def activateProfile(name: String, version: String): AnyRef = {

    val asString = s"${name}-${version}"

    env match {
      case Some(UpdateEnv(_, _, Some(lookupFile), _, _)) =>
        implicit val timeout = Timeout(5, MINUTES)
        val reqId = UUID.randomUUID().toString()
        Await.result(
          ask(updater, Updater.ActivateProfile(reqId, name, version)),
          timeout.duration
        ) match {
          case OperationSucceeded(`reqId`) =>
            "Activated: " + asString
          case OperationFailed(`reqId`, reason) =>
            "Activation failed: " + asString + "\nReason: " + reason
          case x =>
            "Activation failed: " + asString + "\nError: " + x
        }
      case _ =>
        sys.error("No updateable environment detected. No profile lookup file defined.")
    }
  }

}
