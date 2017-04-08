package blended.container.context

import java.util.Properties

import blended.container.context.internal.ContainerPropertyResolver

class PropertyResolverException(msg : String) extends Exception(msg)

/**
 * Each container within the infrastructure has a unique ID. Once the unique ID is assigned to
 * a container, it doesn't change and also survives container restarts.
 * A set of user defined properties can be associated with the container id. This can be used
 * within the registration process at the data center and also to provide a simple mechanism for
 * container meta data.
 */
trait ContainerIdentifierService {
  def getUUID(): String
  def getProperties(): Properties
  def getContainerContext(): ContainerContext

  def resolvePropertyString(value: String) : String = ContainerPropertyResolver.resolve(this, value)
}

object ContainerIdentifierService {
  val containerId = "containerId"
}