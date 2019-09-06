package blended.jmx.impl

import javax.management.openmbean.OpenType
import javax.management.{Attribute, AttributeList, AttributeNotFoundException, DynamicMBean, MBeanInfo}

class GenericImmutableOpenMBean(mBeanInfo: MBeanInfo, attributes: Map[String, GenericImmutableOpenMBean.Element]) extends DynamicMBean {

  override def getAttribute(attribute: String): AnyRef =
    attributes.get(attribute).map(_._1)
      .getOrElse(throw new AttributeNotFoundException(s"Cannot find attribute [${attribute}]"))

  override def setAttribute(attribute: Attribute): Unit =
    throw new AttributeNotFoundException("No attribute can be set in this immutable MBean")

  override def getAttributes(attributes: Array[String]): AttributeList = {
    val result = new AttributeList(attributes.size)
    attributes.foreach(a => result.add(getAttribute(a)))
    result
  }

  override def setAttributes(attributes: AttributeList): AttributeList =
    throw new AttributeNotFoundException("No attribute can be set in this immutable MBean")

  override def invoke(actionName: String, params: Array[AnyRef], signature: Array[String]): AnyRef = ???

  override def getMBeanInfo: MBeanInfo = mBeanInfo
}

object GenericImmutableOpenMBean {
  type Element = (AnyRef, OpenType[_])
}
