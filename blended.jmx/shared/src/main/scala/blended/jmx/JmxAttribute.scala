package blended.jmx

import javax.management.ObjectName

import scala.util.Try

case class JmxObject(
  objName : JmxObjectName,
  attributes : Seq[JmxAttribute]
)

case class JmxAttribute(
  name : String,
  v : AttributeValue
)

sealed trait AttributeValue {
  def value : Any
  override def toString : String = value.toString
}

case class StringMsgProperty(override val value : String) extends AttributeValue {
  override def toString : String = "\"" + super.toString + "\""
}

case class UnitAttributeValue(override val value : Unit = ()) extends AttributeValue
case class IntAttributeValue(override val value : Int) extends AttributeValue
case class LongAttributeValue(override val value : Long) extends AttributeValue
case class BooleanAttributeValue(override val value : Boolean) extends AttributeValue
case class ByteAttributeValue(override val value : Byte) extends AttributeValue
case class ShortAttributeValue(override val value : Short) extends AttributeValue
case class FloatAttributeValue(override val value : Float) extends AttributeValue
case class DoubleAttributeValue(override val value : Double) extends AttributeValue

object AttributeValue {

  import scala.language.implicitConversions

  def apply() : AttributeValue = UnitAttributeValue()
  def apply(s : String) : AttributeValue = StringMsgProperty(s)
  def apply(i : Int) : AttributeValue = IntAttributeValue(i)
  def apply(l : Long) : AttributeValue = LongAttributeValue(l)
  def apply(b : Boolean) : AttributeValue = BooleanAttributeValue(b)
  def apply(b : Byte) : AttributeValue = ByteAttributeValue(b)
  def apply(s : Short) : AttributeValue = ShortAttributeValue(s)
  def apply(f : Float) : AttributeValue = FloatAttributeValue(f)
  def apply(d : Double) : AttributeValue = DoubleAttributeValue(d)

  def lift(v : Any) : Try[AttributeValue] = Try {
    Option(v) match {
      case None => apply()
      case Some(o) =>
        o match {
          case u : Unit              => apply()
          case s : String            => apply(s)
          case i : java.lang.Integer => apply(i)
          case l : java.lang.Long    => apply(l)
          case b : java.lang.Boolean => apply(b)
          case b : java.lang.Byte    => apply(b)
          case s : java.lang.Short   => apply(s)
          case f : java.lang.Float   => apply(f)
          case d : java.lang.Double  => apply(d)
          case n : ObjectName        => apply(n.toString())
          case _                     => throw new IllegalArgumentException(s"Unsupported Msg Property type [${o.getClass().getName()}]")
        }
    }
  }

  def unapply(p : AttributeValue) : Any = p.value
}
