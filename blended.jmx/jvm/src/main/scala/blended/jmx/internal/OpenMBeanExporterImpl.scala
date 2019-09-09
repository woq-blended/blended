package blended.jmx.internal

import java.lang.management.ManagementFactory

import scala.util.Try

import blended.jmx.{MBeanRegistrationSupport, OpenMBeanExporter, OpenMBeanMapper}
import javax.management.{MBeanServer, ObjectName}

class OpenMBeanExporterImpl(openMbeanMapper: OpenMBeanMapper) extends OpenMBeanExporter with MBeanRegistrationSupport {

  override protected val mbeanServer: MBeanServer = ManagementFactory.getPlatformMBeanServer()

  def export(product: Product, objectName: ObjectName, replaceExisting: Boolean): Try[Unit] = Try {
    val mbean = openMbeanMapper.mapProduct(product)
    registerMBean(mbean, objectName, replaceExisting)
  }

  override def remove(objectName: ObjectName): Try[Unit] = {
    unregisterMBean(objectName)
  }
}

