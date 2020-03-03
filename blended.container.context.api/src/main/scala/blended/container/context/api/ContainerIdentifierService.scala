package blended.container.context.api

import java.util.UUID

import scala.beans.BeanProperty

/**
 * Each container within the infrastructure has a unique ID. Once the unique ID is assigned to
 * a container, it doesn't change and also survives container restarts.
 * A set of user defined properties can be associated with the container id. This can be used
 * within the registration process at the data center and also to provide a simple mechanism for
 * container meta data.
 */
trait ContainerIdentifierService {
  @BeanProperty
  lazy val uuid : String = UUID.randomUUID().toString()
  @BeanProperty
  val properties : Map[String, String]
}

object ContainerIdentifierService {
  val containerId = "containerId"
}
