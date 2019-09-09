package blended.jmx

import scala.util.Try

import javax.management.ObjectName

trait OpenMBeanExporter {
  import OpenMBeanExporter._

  def export(product: Product, replaceExisting: Boolean = false)(implicit namingStrategy: NamingStrategy): Try[Unit] = {
    export(product, namingStrategy(product), replaceExisting)
  }

  def export(product: Product, objectName: ObjectName, replaceExisting: Boolean): Try[Unit]

  def remove(objectName: ObjectName): Try[Unit]

}

object OpenMBeanExporter {

  type NamingStrategy = PartialFunction[Any, ObjectName]

  val UseFullClassName: NamingStrategy = {
    case mbean =>
      val c = mbean.getClass()
      val domain = c.getPackage().getName()
      val name = c.getSimpleName()
      new ObjectName(s"${domain}:type=${name}")
  }

  implicit val namingStrategy: NamingStrategy = UseFullClassName
}

