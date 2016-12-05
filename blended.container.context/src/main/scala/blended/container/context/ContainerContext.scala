package blended.container.context

import java.util.Properties
import com.typesafe.config.Config

trait ContainerContext {

  def getContainerLogDirectory(): String
  def getContainerDirectory(): String
  def getContainerConfigDirectory(): String
  def getContainerHostname(): String
  // application.conf + application_overlay.conf
  def getContainerConfig(): Config

  @deprecated
  def readConfig(configId: String): Properties

//  @deprecated
//  def writeConfig(configId: String, props: Properties): Unit

}
