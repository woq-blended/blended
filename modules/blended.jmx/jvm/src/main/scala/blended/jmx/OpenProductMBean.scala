package blended.jmx.internal

import javax.management.DynamicMBean
import blended.jmx.{OpenMBeanMapper, JmxObjectName}
import scala.util.Try
import javax.management.AttributeList
import javax.management.MBeanInfo
import javax.management.Attribute

class IncompatibleJmxUpdateException(
  wanted : Product,
  expected : Product
) extends Exception(s"Tried to update MBean of Type [${expected.getClass().getName()}] with instance of [${wanted.getClass().getName()}]")

class OpenProductMBean(
  initialValue : Product,
  val name : JmxObjectName,
  mapper : OpenMBeanMapper
) extends DynamicMBean {

  private var current : Product = initialValue
  private var inner : DynamicMBean = mapper.mapProduct(initialValue)

  override def getAttribute(attribute: String): Object = inner.getAttribute(attribute)
  override def getAttributes(attributes: Array[String]): AttributeList = inner.getAttributes(attributes)
  override def getMBeanInfo(): MBeanInfo = inner.getMBeanInfo()
  override def invoke(actionName: String, params: Array[Object], signature: Array[String]): Object = inner.invoke(actionName, params, signature)
  override def setAttribute(attribute: Attribute): Unit = inner.setAttribute(attribute)
  override def setAttributes(attributes: AttributeList): AttributeList = inner.setAttributes(attributes)

  def update(v : Product) : Try[Unit] = Try {
    if (v.getClass() != current.getClass()) {
      throw new IncompatibleJmxUpdateException(v, current)
    } else {
      current = v
      inner = mapper.mapProduct(v)
    }
  }
}
