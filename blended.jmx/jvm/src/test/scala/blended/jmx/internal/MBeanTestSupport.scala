package blended.jmx.impl

import java.lang.management.ManagementFactory

import scala.reflect.ClassTag
import scala.reflect.classTag

import blended.jmx.MBeanRegistrationSupport
import javax.management.{MBeanServer, ObjectName}

trait MBeanTestSupport extends MBeanRegistrationSupport {

  lazy val mapper = new OpenMBeanMapperImpl()

  override def mbeanServer: MBeanServer = ManagementFactory.getPlatformMBeanServer();

  def attribute[T: ClassTag](name: String): AnyRef = {
    mbeanServer.getAttribute(objectName[T], name)
  }

  def objectName[T: ClassTag]: ObjectName = {
    val runtimeClass = classTag[T].runtimeClass
    new ObjectName(s"${runtimeClass.getPackage().getName()}:type=${runtimeClass.getSimpleName()}")
  }

  def withExport[T <: AnyRef : ClassTag ](mbean: T, objectName: ObjectName = null, replaceExisting: Boolean = true)(f: => Unit) = {
    val name = Option(objectName).getOrElse(this.objectName[T])
    registerMBean(mbean, name, replaceExisting).get
    try {
      f
    } finally {
      unregisterMBean(name)
    }
  }

  def withMappedExport[T <: Product : ClassTag](cc: T, objectName: ObjectName = null, replaceExisting: Boolean = true)(f: => Unit) = {
    val mbean = mapper.mapProduct(cc)
    val name = Option(objectName).getOrElse(this.objectName[T])
    withExport(mbean, name, replaceExisting)(f)
  }

}
