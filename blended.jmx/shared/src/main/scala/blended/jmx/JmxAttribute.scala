package blended.jmx

import javax.management.ObjectName
import javax.management.openmbean.{CompositeData, TabularData}
import scala.collection.JavaConverters._

import scala.util.Try

final case class JmxObject(
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

final case class ObjectNameAttributeValue(override val value : JmxObjectName) extends AttributeValue

final case class ArrayAttributeValue(override val value : List[AttributeValue]) extends AttributeValue{
  override def toString: String = value.mkString("[", ",", "]")
}

final case class CompositeAttributeValue(override val value : Map[String, AttributeValue]) extends AttributeValue
final case class TabularAttributeValue(override val value : List[AttributeValue]) extends AttributeValue

object AttributeValue {

  import scala.language.implicitConversions

  def apply() : AttributeValue = UnitAttributeValue()
  def apply(s : String) : AttributeValue = StringAttributeValue(s)
  def apply(i : Int) : AttributeValue = IntAttributeValue(i)
  def apply(l : Long) : AttributeValue = LongAttributeValue(l)
  def apply(b : Boolean) : AttributeValue = BooleanAttributeValue(b)
  def apply(b : Byte) : AttributeValue = ByteAttributeValue(b)
  def apply(s : Short) : AttributeValue = ShortAttributeValue(s)
  def apply(f : Float) : AttributeValue = FloatAttributeValue(f)
  def apply(d : Double) : AttributeValue = DoubleAttributeValue(d)
  def apply(c : Char) : AttributeValue = CharAttributeValue(c)
  def apply(n : JmxObjectName) : AttributeValue = ObjectNameAttributeValue(n)
  def apply(a : Array[AttributeValue]) : AttributeValue = ArrayAttributeValue(a.toList)
  def apply(m : Map[String, AttributeValue]) = CompositeAttributeValue(m)
  def apply(t : List[AttributeValue]) = TabularAttributeValue(t)

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
          case n : ObjectName        => apply(JmxObjectName(n.getCanonicalName).get)
          case al : Array[Long]      => ArrayAttributeValue.apply(al.map(LongAttributeValue.apply).toList)
          case ab : Array[Boolean]   => ArrayAttributeValue.apply(ab.map(BooleanAttributeValue.apply).toList)
          case ab : Array[Byte]      => ArrayAttributeValue.apply(ab.map(ByteAttributeValue.apply).toList)
          case ac : Array[Char]      => ArrayAttributeValue.apply(ac.map(CharAttributeValue.apply).toList)
          case ad : Array[Double]    => ArrayAttributeValue.apply(ad.map(DoubleAttributeValue.apply).toList)
          case af : Array[Float]     => ArrayAttributeValue.apply(af.map(FloatAttributeValue.apply).toList)
          case ai : Array[Int]       => ArrayAttributeValue.apply(ai.map(IntAttributeValue.apply).toList)
          case as : Array[Short]     => ArrayAttributeValue.apply(as.map(ShortAttributeValue.apply).toList)
          case a : Array[Any]        => apply(a.map(v => lift(v).get))
          case t : TabularData       =>
            val values : List[AttributeValue] = t.values().asScala.map(v => lift(v).get).toList
            apply(values)
          case cd : CompositeData    =>
            val map : Map[String, AttributeValue] = cd.getCompositeType().keySet().asScala.map{ key =>
              (key, lift(cd.get(key)).get)
            }.toMap
            apply(map)
          case _                     =>
            throw new IllegalArgumentException(s"Unsupported Attribute type [${o.getClass().getName()}]")
        }
    }
  }

  def unapply[T](p : AttributeValue) : Any = p.value
}
