package blended.jmx

import scala.reflect.ClassTag

sealed trait MBeanUpdateEvent
/**
  * Update or create an MBean instance with the content of the case class 
  * as attributes. The MBean will be created if it does not yet exist. 
  * The MBean name will be derived from a registered Naming Strategy.
  */
case class UpdateMBean[T <: Product](v : T)(implicit val cTag : ClassTag[T]) extends MBeanUpdateEvent

/**
  * Remove an MBean. 
  */
case class RemoveMBean[T <: Product](v : T)(implicit val cTag : ClassTag[T]) extends MBeanUpdateEvent
