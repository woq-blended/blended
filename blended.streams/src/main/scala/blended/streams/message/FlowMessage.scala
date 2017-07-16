package blended.streams.message

import akka.NotUsed
import akka.util.ByteString

sealed trait MsgProperty {
  def value() : Any

  override def toString: String = value().toString()
}

case class StringMsgProperty(value: String) extends MsgProperty
case class IntMsgProperty(value: Int) extends MsgProperty
case class LongMsgProperty(value: Long) extends MsgProperty
case class BooleanMsgProperty(value : Boolean) extends MsgProperty

case object MsgProperty {

  import scala.language.implicitConversions

  implicit def stringToProp(s: String) : MsgProperty = StringMsgProperty(s)
  implicit def intToProp(i : Int) : MsgProperty = IntMsgProperty(i)
  implicit def longToProp(l : Long) : MsgProperty = LongMsgProperty(l)
  implicit def boolToProp(b : Boolean) : MsgProperty = BooleanMsgProperty(b)

  def apply(s : String) : MsgProperty = new StringMsgProperty(s)
  def apply(i : Int) : MsgProperty = new IntMsgProperty(i)
  def apply(l : Long) : MsgProperty = new LongMsgProperty(l)
  def apply(b : Boolean) : MsgProperty = new BooleanMsgProperty(b)

  def unapply(p: MsgProperty): Any = p match {
    case s : StringMsgProperty => s.value
    case i : IntMsgProperty => i.value
    case l : LongMsgProperty => l.value
    case b : BooleanMsgProperty => b.value
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
