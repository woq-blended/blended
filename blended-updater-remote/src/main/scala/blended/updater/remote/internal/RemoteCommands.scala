package blended.updater.remote.internal

import blended.updater.remote.RemoteUpdater
import java.io.File
import blended.updater.config.RuntimeConfig
import com.typesafe.config.ConfigFactory
import blended.mgmt.base.StageProfile

class RemoteCommands(updater: RemoteUpdater) {

  def commands = Seq(
    "remoteShow" -> "Show update information about remote container",
    "remoteStage" -> "Stage a profile",
    "registerProfile" -> "Register a profile",
    "profiles" -> "Show all registered profiles"
  )

  def remoteShow(): String = {
    updater.getContainerIds.map { id =>
      s"Outstanding actions of container with ID ${id}:\n${updater.getContainerActions(id).mkString("\n")}"
    }.mkString("\n")
  }

  def remoteShow(containerId: String): String = {
    if (updater.getContainerIds().exists(_ == containerId)) {
      s"Outstanding actions of container with ID ${containerId}:\n${updater.getContainerActions(containerId).mkString("\n")}"
    } else {
      s"Unknown container ID: ${containerId}"
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
        updater.addAction(containerId, StageProfile(rc))
        println(s"Scheduled profile staging for container with ID ${containerId}. Config: ${rc}")
    }
  }

}