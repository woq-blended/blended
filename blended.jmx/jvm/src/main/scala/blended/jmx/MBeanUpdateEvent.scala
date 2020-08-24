package blended.jmx

import scala.reflect.ClassTag

sealed trait MBeanUpdateEvent

/**
  * Use this message to register a NamingStrategy for a case class
  */
case class RegisterNamingStrategy[T <: Product](ns : NamingStrategy)(implicit val cTag : ClassTag[T]) extends MBeanUpdateEvent
/**
  * Update or create an MBean instance with the content of the case class 
  * as attributes. The MBean will be created if it does not yet exist. 
  * The MBean name will be derived from the registered Naming Strategy.
  */
case class UpdateMBean[T <: Product](v : T)(implicit val cTag : ClassTag[T]) extends MBeanUpdateEvent

/**
  * Remove an MBean. 
  */
case class RemoveMBean[T <: Product](v : T)(implicit val cTag : ClassTag[T]) extends MBeanUpdateEvent
