package blended.jmx.impl

import java.lang.management.ManagementFactory

import scala.reflect.ClassTag
import scala.reflect.classTag

import javax.management.ObjectName

trait MBeanTestSupport {

  lazy val mapper = new OpenMBeanMapperImpl()

  def server = ManagementFactory.getPlatformMBeanServer();

  def attribute[T: ClassTag](name: String): AnyRef = {
    server.getAttribute(objectName[T], name)
  }

  def objectName[T: ClassTag]: ObjectName = {
    val runtimeClass = classTag[T].runtimeClass
    new ObjectName(s"${runtimeClass.getPackage().getName()}:type=${runtimeClass.getSimpleName()}")
  }

  def withExport[T: ClassTag](mbean: T, objectName: ObjectName = null, replaceExisting: Boolean = true)(f: => Unit) = {
    val name = Option(objectName).getOrElse(this.objectName[T])
    if (replaceExisting && server.isRegistered(name)) {
      server.unregisterMBean(name)
    }
    server.registerMBean(mbean, name)
    try {
      f
    } finally {
      server.unregisterMBean(name)
    }
  }

  def withMappedExport[T <: Product : ClassTag](cc: T, objectName: ObjectName = null, replaceExisting: Boolean = true)(f: => Unit) = {
    val mbean = mapper.mapProduct(cc)
    val name = Option(objectName).getOrElse(this.objectName[T])
    withExport(mbean, name, replaceExisting)(f)
  }

}
