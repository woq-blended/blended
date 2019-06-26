package blended.jmx.json

import blended.jmx._
import prickle.{CompositePickler, PicklerPair}

object PrickleProtocol {

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
