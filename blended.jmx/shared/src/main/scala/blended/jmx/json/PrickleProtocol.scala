package blended.jmx.json

import blended.jmx
import blended.jmx._
import prickle._

object PrickleProtocol {

  implicit val objNamePickler : Pickler[JmxObjectName] = Pickler.materializePickler[JmxObjectName]
  implicit val objNameUnpickler : Unpickler[JmxObjectName] = Unpickler.materializeUnpickler[jmx.JmxObjectName]

  implicit val infoPickler : Pickler[JmxBeanInfo] = Pickler.materializePickler[JmxBeanInfo]
  implicit val infoUnpickler : Unpickler[JmxBeanInfo] = Unpickler.materializeUnpickler[JmxBeanInfo]

  implicit val attributeValuePickler : PicklerPair[AttributeValue] = CompositePickler[AttributeValue]
    .concreteType[StringAttributeValue]
    .concreteType[UnitAttributeValue]
    .concreteType[IntAttributeValue]
    .concreteType[LongAttributeValue]
    .concreteType[BooleanAttributeValue]
    .concreteType[ByteAttributeValue]
    .concreteType[ShortAttributeValue]
    .concreteType[FloatAttributeValue]
    .concreteType[DoubleAttributeValue]
    .concreteType[CharAttributeValue]
    .concreteType[ObjectNameAttributeValue]
    .concreteType[ArrayAttributeValue]
    .concreteType[TabularAttributeValue]
    .concreteType[CompositeAttributeValue]
}

