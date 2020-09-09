package blended.jmx.json

import blended.jmx._
import prickle._

import scala.collection.mutable
import scala.util.Try

object PrickleProtocol {

  implicit val objNamePickler : Pickler[JmxObjectName] = Pickler.materializePickler[JmxObjectName]
  implicit val objectNameUnpickler : Unpickler[JmxObjectName] = Unpickler.materializeUnpickler[JmxObjectName]

  implicit val beanInfoPickler : Pickler[JmxBeanInfo] = Pickler.materializePickler[JmxBeanInfo]
  implicit val beanInfoUnpickler : Unpickler[JmxBeanInfo] = Unpickler.materializeUnpickler[JmxBeanInfo]

  implicit val bigIntPickler : Pickler[BigInt] = new Pickler[BigInt] {
    override def pickle[P](obj: BigInt, state: PickleState)(implicit config: PConfig[P]): P = {
      config.makeString(obj.toString())
    }
  }

  implicit val bigIntUnpickler : Unpickler[BigInt] = new Unpickler[BigInt] {

    override def unpickle[P](pickle: P, state: mutable.Map[String, Any])(implicit config: PConfig[P]): Try[BigInt] = {
      val s = config.readString(pickle)
      s.map(BigInt(_))
    }
  }

  implicit val bigDecPickler : Pickler[BigDecimal] = new Pickler[BigDecimal] {
    override def pickle[P](obj: BigDecimal, state: PickleState)(implicit config: PConfig[P]): P = {
      config.makeString(obj.toString())
    }
  }

  implicit val bigDecUnpickler : Unpickler[BigDecimal] = new Unpickler[BigDecimal] {

    override def unpickle[P](pickle: P, state: mutable.Map[String, Any])(implicit config: PConfig[P]): Try[BigDecimal] = {
      val s = config.readString(pickle)
      s.map(BigDecimal(_))
    }
  }

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
    .concreteType[ListAttributeValue]
    .concreteType[TabularAttributeValue]
    .concreteType[BigIntegerAtrributeValue]
    .concreteType[BigDecimalAtrributeValue]
    .concreteType[CompositeAttributeValue]
}
