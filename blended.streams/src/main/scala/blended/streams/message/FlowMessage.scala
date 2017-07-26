package blended.streams.message

import akka.NotUsed
import akka.util.ByteString

import scala.util.Try

sealed trait MsgProperty {
  def value() : Any
  override def toString: String = value().toString()
}

case class StringMsgProperty(value: String) extends MsgProperty
case class IntMsgProperty(value: Int) extends MsgProperty
case class LongMsgProperty(value: Long) extends MsgProperty
case class BooleanMsgProperty(value : Boolean) extends MsgProperty
case class ByteMsgProperty(value : Byte) extends MsgProperty
case class ShortMsgProperty(value : Short) extends MsgProperty
case class FloatMsgProperty(value: Float) extends MsgProperty
case class DoubleMsgProperty(value: Double) extends MsgProperty

case object MsgProperty {

  import scala.language.implicitConversions

  implicit def stringToProp(s: String) : MsgProperty = StringMsgProperty(s)
  implicit def intToProp(i : Int) : MsgProperty = IntMsgProperty(i)
  implicit def longToProp(l : Long) : MsgProperty = LongMsgProperty(l)
  implicit def boolToProp(b : Boolean) : MsgProperty = BooleanMsgProperty(b)
  implicit def byteToProp(b : Byte) : MsgProperty = ByteMsgProperty(b)
  implicit def shortToProp(s : Short) : MsgProperty = ShortMsgProperty(s)
  implicit def floatToProp(f : Float) : MsgProperty = FloatMsgProperty(f)
  implicit def doubleToProp(d : Double) : MsgProperty = DoubleMsgProperty(d)

  def apply(s : String) : MsgProperty = new StringMsgProperty(s)
  def apply(i : Int) : MsgProperty = new IntMsgProperty(i)
  def apply(l : Long) : MsgProperty = new LongMsgProperty(l)
  def apply(b : Boolean) : MsgProperty = new BooleanMsgProperty(b)
  def apply(b : Byte) : MsgProperty = new ByteMsgProperty(b)
  def apply(s : Short) : MsgProperty = new ShortMsgProperty(s)
  def apply(f : Float) : MsgProperty = new FloatMsgProperty(f)
  def apply(d : Double) : MsgProperty = new DoubleMsgProperty(d)

  def lift(o : AnyRef) : Try[MsgProperty] = Try {
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

  def unapply(p: MsgProperty): Any = p match {
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

sealed abstract class FlowMessage(h: Map[String, MsgProperty]) {

  def body() : Any
  def header : Map[String, MsgProperty] = h

  override def toString: String = s"${getClass().getSimpleName()}(${header})($body)"
}

class BaseFlowMessage(override val header: Map[String, MsgProperty]) extends FlowMessage(header) {
  override def body(): Any = NotUsed
}

class BinaryFlowMessage(header: Map[String, MsgProperty], content: ByteString) extends FlowMessage(header) {
  override def body(): Any = content
  def getBytes() = content
}

class TextFlowMessage(header: Map[String, MsgProperty], content: String) extends FlowMessage(header) {
  override def body(): Any = content
  def getText() = content
}

case object FlowMessage {

  def apply(props: Map[String, MsgProperty]) = new BaseFlowMessage(props)
  def apply(props : Map[String, MsgProperty], content : String) = new TextFlowMessage(props, content)
  def apply(props : Map[String, MsgProperty], content: ByteString) = new BinaryFlowMessage(props, content)

}
