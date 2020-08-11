package blended.jmx.internal

import java.lang.management.ManagementFactory

import blended.jmx.{MBeanRegistrationSupport, OpenMBeanExporter, OpenMBeanMapper}
import blended.util.logging.Logger
import javax.management.{MBeanServer, ObjectName}

import scala.util.Try

class OpenMBeanExporterImpl(openMbeanMapper: OpenMBeanMapper) extends OpenMBeanExporter with MBeanRegistrationSupport {

  private[this] val log : Logger = Logger[OpenMBeanExporterImpl]

  private[this] lazy val _mbeanServer: MBeanServer = ManagementFactory.getPlatformMBeanServer()
  override protected def mbeanServer: MBeanServer = _mbeanServer

  def export(product: Product, objectName: ObjectName, replaceExisting: Boolean): Try[Unit] = {
    Try { openMbeanMapper.mapProduct(product) }
      .flatMap{ mbean =>
        log.trace(s"Calling registerMBean for [${objectName.toString()}] with [$product], replaceExisting [$replaceExisting]")
        registerMBean(mbean, objectName, replaceExisting)
      }
  }

  override def remove(objectName: ObjectName): Try[Unit] = {
    unregisterMBean(objectName)
  }
}

