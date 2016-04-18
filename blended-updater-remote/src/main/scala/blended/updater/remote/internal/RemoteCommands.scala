package blended.updater.remote.internal

import blended.mgmt.base.AddOverlayConfig
import blended.mgmt.base.AddRuntimeConfig
import blended.updater.remote.RemoteUpdater
import java.io.File
import blended.updater.config.RuntimeConfig
import com.typesafe.config.ConfigFactory
import blended.mgmt.base.StageProfile
import blended.mgmt.base.ActivateProfile
import blended.updater.remote.ContainerState
import blended.mgmt.base.StageProfile
import java.util.Date

class RemoteCommands(updater: RemoteUpdater) {

  def commands = Seq(
    "remoteShow" -> "Show update information about remote container",
    "remoteStage" -> "Stage a profile for a remote container",
    "remoteActivate" -> "Activate a profile for a remote container",
    "registerProfile" -> "Register a profile",
    "profiles" -> "Show all registered profiles"
  )

  def renderContainerState(state: ContainerState): String = {
    s"""Container ID: ${state.containerId}
        |  active profile: ${state.activeProfile.mkString}
        |  valid profiles: ${state.validProfiles.mkString(", ")}
        |  invalid profiles: ${state.invalidProfiles.mkString(", ")}
        |  outstanding actions: ${
      state.outstandingActions.map {
        // TODO: overlays
        case AddRuntimeConfig(rc, _) => s"add runtime config ${rc.name}-${rc.version}"
        case AddOverlayConfig(oc, _) => s"add overlay config ${oc.name}-${oc.version}"
        case StageProfile(n, v, o, _) => s"stage ${n}-${v} with ${o.toList.sorted.mkString(" and ")}"
        case ActivateProfile(n, v, o, _) => s"activate ${n}-${v} with ${o.toList.sorted.mkString(" and ")}"
      }.mkString(", ")
    }
        |  last sync: ${state.syncTimeStamp.map(s => new Date(s)).mkString}""".stripMargin
  }

  def remoteShow(): String = {
    updater.getContainerIds.map { id =>
      s"Update state of container with ID ${id}:\n${updater.getContainerState(id).map(renderContainerState)}\n"
    }.mkString("\n")
  }

  def remoteShow(containerId: String): String = {
    updater.getContainerState(containerId) match {
      case Some(state) => s"Update state of container with ID ${containerId}:\n${state}\n"
      case None => s"Unknown container ID: ${containerId}"
    }
  }

  def profiles(): String = {
    updater.getRuntimeConfigs().map(rc => s"${rc.name}-${rc.version}").mkString("\n")
  }

  def registerProfile(profileFile: String): Unit = {
    val file = new File(profileFile)
    if (!file.exists()) {
      println(s"File ${file.toURI()} does not exist")
    } else {
      println(s"Reading profile from file: ${file.toURI()}")
      val config = ConfigFactory.parseFile(file).resolve()
      val runtimeConfig = RuntimeConfig.read(config)
      println(s"Profile: ${runtimeConfig}")
      updater.registerRuntimeConfig(runtimeConfig.get)
    }
  }

  def remoteStage(containerId: String, profileName: String, profileVersion: String): Unit = {
    updater.getRuntimeConfigs().find(rc => rc.name == profileName && rc.version == profileVersion) match {
      case None => println(s"Profile '${profileName}-${profileVersion}' not found")
      case Some(rc) =>
        // FIXME: support for overlay
        updater.addAction(containerId, AddRuntimeConfig(rc))
        updater.addAction(containerId, StageProfile(profileName, profileVersion, Set()))
        println(s"Scheduled profile staging for container with ID ${containerId}. Config: ${profileName}-${profileVersion}")
    }
  }

  def remoteActivate(containerId: String, profileName: String, profileVersion: String): Unit = {
    // FIXME: support for overlays
    updater.addAction(containerId, ActivateProfile(profileName, profileVersion, Set()))
    println(s"Scheduled profile activation for container with ID ${containerId}. Profile: ${profileName}-${profileVersion}")
  }

}