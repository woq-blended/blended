package blended.jmx

import scala.util.Try

trait BlendedMBeanServerFacade {

  def mbeanNames() : Try[List[JmxObjectName]]
  def mbeanInfo(objName : JmxObjectName) : Unit

}
