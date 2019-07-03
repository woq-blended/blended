package blended.jmx

import scala.util.Try

trait BlendedMBeanServerFacade {

  def mbeanInfo(objName : JmxObjectName) : Try[JmxBeanInfo]
  def allMbeanNames() : Try[List[JmxObjectName]] = mbeanNames(None)
  def mbeanNames(objName : Option[JmxObjectName]) : Try[List[JmxObjectName]]

}
