package blended.jmx

import scala.util.Try

trait BlendedMBeanServerFacade {

  def getMBeanNames() : Try[List[JmxObjectName]]

}
