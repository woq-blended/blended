package blended.container.context.api

import com.typesafe.config.Config

import scala.annotation.meta.beanGetter

trait ContainerContext {

  def getContainerDirectory() : String
  def getContainerConfigDirectory() : String
  def getContainerLogDirectory(): String

  def getProfileDirectory(): String
  def getProfileConfigDirectory(): String

  def getContainerHostname(): String

  // application.conf + application_overlay.conf
  def getContainerConfig(): Config
}
