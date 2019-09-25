package blended.jmx.internal

import java.lang.management.ManagementFactory

import scala.util.Try

import blended.jmx.{MBeanRegistrationSupport, OpenMBeanExporter, OpenMBeanMapper}
import javax.management.{MBeanServer, ObjectName}

class OpenMBeanExporterImpl(openMbeanMapper: OpenMBeanMapper) extends OpenMBeanExporter with MBeanRegistrationSupport {

  private[this] lazy val _mbeanServer: MBeanServer = ManagementFactory.getPlatformMBeanServer()
  override protected def mbeanServer: MBeanServer = _mbeanServer

  def export(product: Product, objectName: ObjectName, replaceExisting: Boolean): Try[Unit] = {
    Try { openMbeanMapper.mapProduct(product) }
      .flatMap{ mbean => registerMBean(mbean, objectName, replaceExisting) }
  }

  override def remove(objectName: ObjectName): Try[Unit] = {
    unregisterMBean(objectName)
  }
}

