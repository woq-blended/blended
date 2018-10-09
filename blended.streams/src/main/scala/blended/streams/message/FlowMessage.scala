package blended.streams.message

import akka.NotUsed
import akka.util.ByteString

import scala.util.Try

sealed trait MsgProperty[T <: Any] {
  def value : T
  override def toString: String = value.toString()
}

case class StringMsgProperty(override val value: String) extends MsgProperty[String]
case class IntMsgProperty(override val value: Int) extends MsgProperty[Int]
case class LongMsgProperty(override val value: Long) extends MsgProperty[Long]
case class BooleanMsgProperty(override val value : Boolean) extends MsgProperty[Boolean]
case class ByteMsgProperty(override val value : Byte) extends MsgProperty[Byte]
case class ShortMsgProperty(override val value : Short) extends MsgProperty[Short]
case class FloatMsgProperty(override val value: Float) extends MsgProperty[Float]
case class DoubleMsgProperty(override val value: Double) extends MsgProperty[Double]

object MsgProperty {

  import scala.language.implicitConversions

  implicit def stringToProp(s: String) : MsgProperty[String] = StringMsgProperty(s)
  implicit def intToProp(i : Int) : MsgProperty[Int] = IntMsgProperty(i)
  implicit def longToProp(l : Long) : MsgProperty[Long] = LongMsgProperty(l)
  implicit def boolToProp(b : Boolean) : MsgProperty[Boolean] = BooleanMsgProperty(b)
  implicit def byteToProp(b : Byte) : MsgProperty[Byte] = ByteMsgProperty(b)
  implicit def shortToProp(s : Short) : MsgProperty[Short] = ShortMsgProperty(s)
  implicit def floatToProp(f : Float) : MsgProperty[Float] = FloatMsgProperty(f)
  implicit def doubleToProp(d : Double) : MsgProperty[Double] = DoubleMsgProperty(d)

  def apply(s : String) : MsgProperty[String] = StringMsgProperty(s)
  def apply(i : Int) : MsgProperty[Int] = IntMsgProperty(i)
  def apply(l : Long) : MsgProperty[Long] = LongMsgProperty(l)
  def apply(b : Boolean) : MsgProperty[Boolean] = BooleanMsgProperty(b)
  def apply(b : Byte) : MsgProperty[Byte] = ByteMsgProperty(b)
  def apply(s : Short) : MsgProperty[Short] = ShortMsgProperty(s)
  def apply(f : Float) : MsgProperty[Float] = FloatMsgProperty(f)
  def apply(d : Double) : MsgProperty[Double] = DoubleMsgProperty(d)

  def lift(o : Any) : Try[MsgProperty[_]] = Try {
    o match {
      case s: String => apply(s)
      case i: Integer => apply(i)
      case l: java.lang.Long => apply(l)
      case b: java.lang.Boolean => apply(b)
      case b: java.lang.Byte => apply(b)
      case s: java.lang.Short => apply(s)
      case f: java.lang.Float => apply(f)
      case d: java.lang.Double => apply(d)
      case _ => throw new IllegalArgumentException("Unsupported Msg Property type")
    }
  }

  def unapply(p: MsgProperty[_]): Any = p match {
    case s : StringMsgProperty => s.value
    case i : IntMsgProperty => i.value
    case l : LongMsgProperty => l.value
    case b : BooleanMsgProperty => b.value
    case b : ByteMsgProperty => b.value
    case s : ShortMsgProperty => s.value
    case f : FloatMsgProperty => f.value
    case d : DoubleMsgProperty => d.value
  }
}

sealed abstract class FlowMessage(h: Map[String, MsgProperty[_]]) {

  def body() : Any
  def header : Map[String, MsgProperty[_]] = h

  def header[T](name : String): Option[T] = header.get(name) match {
    case Some(v) if v.value.isInstanceOf[T] => Some(v.value.asInstanceOf[T])
    case Some(_) => None
    case _ => None
  }

  def headerWithDefault[T <: AnyVal](name: String, default: T) : T = header[T](name) match {
    case Some(v) => v
    case None => default
  }

  override def toString: String = s"${getClass().getSimpleName()}($header)($body)"
}

case class BaseFlowMessage(override val header: Map[String, MsgProperty[_]]) extends FlowMessage(header) {
  override def body(): Any = NotUsed
}

case class BinaryFlowMessage(override val header: Map[String, MsgProperty[_]], content: ByteString) extends FlowMessage(header) {
  override def body(): Any = content
  def getBytes() : ByteString = content
}

case class TextFlowMessage(override val header: Map[String, MsgProperty[_]], content: String) extends FlowMessage(header) {
  override def body(): Any = content
  def getText(): String = content
}

object FlowMessage {

  def apply(props: Map[String, MsgProperty[_]]): FlowMessage = BaseFlowMessage(props)
  def apply(content : String, props : Map[String, MsgProperty[_]]): FlowMessage= TextFlowMessage(props, content)
  def apply(content: ByteString, props : Map[String, MsgProperty[_]]):FlowMessage = BinaryFlowMessage(props, content)

}
