package blended.jmx

final case class JmxBeanInfo(
  objName : JmxObjectName,
  attributes : CompositeAttributeValue
)

final case class JmxAttribute(
  name : String,
  v : AttributeValue
)

sealed trait AttributeValue {
  def value : Any
  override def toString : String = value.toString
}

case class StringAttributeValue(override val value : String) extends AttributeValue {
  override def toString : String = "\"" + super.toString + "\""
}


final case class UnitAttributeValue(override val value : Unit = ()) extends AttributeValue
final case class IntAttributeValue(override val value : Int) extends AttributeValue
final case class LongAttributeValue(override val value : Long) extends AttributeValue
final case class BooleanAttributeValue(override val value : Boolean) extends AttributeValue
final case class ByteAttributeValue(override val value : Byte) extends AttributeValue
final case class ShortAttributeValue(override val value : Short) extends AttributeValue
final case class FloatAttributeValue(override val value : Float) extends AttributeValue
final case class DoubleAttributeValue(override val value : Double) extends AttributeValue
final case class CharAttributeValue(override val value : Char) extends AttributeValue

final case class BigIntegerAtrributeValue(override val value : BigInt) extends AttributeValue
final case class BigDecimalAtrributeValue(override val value : BigDecimal) extends AttributeValue

final case class ObjectNameAttributeValue(override val value : JmxObjectName) extends AttributeValue

final case class ListAttributeValue(override val value : List[AttributeValue]) extends AttributeValue{
  override def toString: String = value.mkString("[", ",", "]")
}

final case class CompositeAttributeValue(override val value : Map[String, AttributeValue]) extends AttributeValue
final case class TabularAttributeValue(override val value : List[AttributeValue]) extends AttributeValue
