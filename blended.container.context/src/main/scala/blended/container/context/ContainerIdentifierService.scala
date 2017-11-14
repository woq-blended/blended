package blended.container.context

class PropertyResolverException(msg : String) extends Exception(msg)

/**
 * Each container within the infrastructure has a unique ID. Once the unique ID is assigned to
 * a container, it doesn't change and also survives container restarts.
 * A set of user defined properties can be associated with the container id. This can be used
 * within the registration process at the data center and also to provide a simple mechanism for
 * container meta data.
 */
trait ContainerIdentifierService {
  val uuid: String
  val properties : Map[String,String]
  val containerContext: ContainerContext

  def resolvePropertyString(value: String) : String = ContainerPropertyResolver.resolve(this, value)
}

object ContainerIdentifierService {
  val containerId = "containerId"
}