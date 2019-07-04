package blended.jmx.json

import blended.jmx._
import prickle.{CompositePickler, Pickler, PicklerPair, Unpickler}

object PrickleProtocol {

  implicit val objNamePickler : Pickler[JmxObjectName] = Pickler.materializePickler[JmxObjectName]
  implicit val objectNameUnpickler : Unpickler[JmxObjectName] = Unpickler.materializeUnpickler[JmxObjectName]

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
