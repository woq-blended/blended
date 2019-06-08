package blended.jmx.internal

import blended.jmx.{BlendedMBeanServerFacade, JmxObjectName, JmxObjectNameCompanion}
import javax.management.{MBeanServer, ObjectName}

import scala.collection.mutable
import scala.util.Try

class BlendedMBeanServerFacadeImpl(svr : MBeanServer) extends BlendedMBeanServerFacade {

  override def getMBeanNames(): Try[List[JmxObjectName]] = Try {
    val names : mutable.ListBuffer[ObjectName] = mutable.ListBuffer.empty[ObjectName]
    // scalastyle:off null
    svr.queryNames(null, null).forEach(n => names.append(n))
    // scalastyle:on null
    names.toList.map(on => JmxObjectNameCompanion.createJmxObjectName(on).get)
  }
}
