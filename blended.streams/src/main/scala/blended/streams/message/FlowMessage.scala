package blended.streams.message

import akka.NotUsed
import akka.util.ByteString

import scala.util.Try

sealed trait MsgProperty[T] {
  def value : T
  override def toString: String = value.toString
}

case class StringMsgProperty(override val value: String) extends MsgProperty[String] {
  override def toString: String = "\"" + super.toString + "\""
}

case class IntMsgProperty(override val value: Int) extends MsgProperty[Int]
case class LongMsgProperty(override val value: Long) extends MsgProperty[Long]
case class BooleanMsgProperty(override val value : Boolean) extends MsgProperty[Boolean]
case class ByteMsgProperty(override val value : Byte) extends MsgProperty[Byte]
case class ShortMsgProperty(override val value : Short) extends MsgProperty[Short]
case class FloatMsgProperty(override val value: Float) extends MsgProperty[Float]
case class DoubleMsgProperty(override val value: Double) extends MsgProperty[Double]

object MsgProperty {

  import scala.language.implicitConversions

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
      case i: java.lang.Integer => apply(i)
      case l: java.lang.Long => apply(l)
      case b: java.lang.Boolean => apply(b)
      case b: java.lang.Byte => apply(b)
      case s: java.lang.Short => apply(s)
      case f: java.lang.Float => apply(f)
      case d: java.lang.Double => apply(d)
      case _ => throw new IllegalArgumentException(s"Unsupported Msg Property type [${o.getClass().getName()}]")
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

object FlowMessage {

  type FlowMessageProps = Map[String, MsgProperty[_]]

  def props(m :(String, Any)*) : Try[FlowMessageProps] = Try {
    m.map { case (k, v) =>
      val p : MsgProperty[_] = MsgProperty.lift(v).get
      k -> p
    }.toMap
  }

  val noProps : FlowMessageProps = Map.empty[String, MsgProperty[_]]

  def apply(props: FlowMessageProps): FlowMessage = BaseFlowMessage(props)
  def apply(content : String)(props : FlowMessageProps) : FlowMessage= TextFlowMessage(content, props)
  def apply(content: ByteString)(props : FlowMessageProps) : FlowMessage = BinaryFlowMessage(content, props)
  def apply(content: Array[Byte])(props : FlowMessageProps) : FlowMessage = BinaryFlowMessage(content, props)

}

import blended.streams.message.FlowMessage.FlowMessageProps

sealed abstract class FlowMessage(msgHeader: FlowMessageProps) {

  def body() : Any
  def header : FlowMessageProps = msgHeader

  def bodySize() : Int

  def header[T](name : String)(implicit m : Manifest[T]) : Option[T] = {

    def classMatch(v : Any, clazz: Class[_]) : Boolean = {

      val intClasses : Seq[Class[_]] = Seq(classOf[Integer], classOf[Int])
      val longClasses  : Seq[Class[_]] = Seq(classOf[java.lang.Long], classOf[Long])
      val shortClasses : Seq[Class[_]] = Seq(classOf[java.lang.Short], classOf[Short])
      val floatClasses : Seq[Class[_]] = Seq(classOf[java.lang.Float], classOf[Float])
      val doubleClasses : Seq[Class[_]] = Seq(classOf[java.lang.Double], classOf[Double])
      val boolClasses : Seq[Class[_]] = Seq(classOf[java.lang.Boolean], classOf[Boolean])
      val byteClasses : Seq[Class[_]] = Seq(classOf[java.lang.Byte], classOf[Byte])

      val matches : Map[Class[_], Seq[Class[_]]] = Map(
        classOf[java.lang.Integer] -> intClasses,
        classOf[Int] -> intClasses,
        classOf[java.lang.Long] -> longClasses,
        classOf[Long] -> longClasses,
        classOf[Short] -> shortClasses,
        classOf[java.lang.Short] -> shortClasses,
        classOf[java.lang.Float] -> floatClasses,
        classOf[Float] -> floatClasses,
        classOf[java.lang.Double] -> doubleClasses,
        classOf[Double] -> doubleClasses,
        classOf[java.lang.Boolean] -> boolClasses,
        classOf[Boolean] -> boolClasses,
        classOf[java.lang.Byte] -> byteClasses,
        classOf[Byte] -> byteClasses
      )

      if (v.getClass() == clazz) {
        true
      } else {
        matches.get(v.getClass()).exists { clazzes =>
          clazzes.exists(c => v.getClass().isAssignableFrom(c))
        }
      }
    }

    header.get(name) match {
      case Some(v) if classMatch(v.value, m.runtimeClass) => Some(v.value.asInstanceOf[T])
      case Some(_) => None
      case _ => None
    }
  }

  def headerWithDefault[T](name: String, default: T)(implicit m : Manifest[T]) : T = header[T](name) match {
    case Some(v) => v
    case None => default
  }

  def removeHeader(keys: String*) : FlowMessage

  def withHeaders(header : FlowMessageProps) : Try[FlowMessage] = Try {
    header.foldLeft(this) { case (current, (key, prop)) =>
      current.withHeader(key, prop.value).get
    }
  }

  def withHeader(keys : String, value: Any, overwrite: Boolean = true) : Try[FlowMessage]

  protected def doRemoveHeader(keys : String*) : FlowMessageProps = header.filter(k => !keys.contains(k))

  protected def newHeader(key : String, value: Any, overwrite: Boolean) : Try[FlowMessageProps] = Try {
    if (overwrite) {
      header.filterKeys(_ != key) + (key -> MsgProperty.lift(value).get)
    } else {
      if (header.isDefinedAt(key)) {
        header
      } else {
        header + (key -> MsgProperty.lift(value).get)
      }
    }
  }


  override def toString: String = s"${getClass().getSimpleName()}($header)($body)"
}

case class BaseFlowMessage(override val header: FlowMessageProps) extends FlowMessage(header) {
  override def body(): Any = NotUsed


  override def bodySize(): Int = 0

  override def withHeader(key: String, value: Any, overwrite: Boolean = true): Try[FlowMessage] = Try {
    copy(header = newHeader(key, value, overwrite).get)
  }

  override def removeHeader(keys: String*): FlowMessage = copy(header = doRemoveHeader(keys:_*))
}

object BinaryFlowMessage {

  def apply(bytes: Array[Byte], msgHeader : FlowMessageProps) : BinaryFlowMessage = BinaryFlowMessage(ByteString(bytes), msgHeader)

  //TODO: Initialize from Byte Stream
}

case class BinaryFlowMessage(content : ByteString, override val header: FlowMessageProps) extends FlowMessage(header) {
  override def body(): Any = content

  def getBytes() : ByteString = content

  override def bodySize(): Int = content.size

  override def withHeader(key: String, value: Any, overwrite: Boolean = true): Try[FlowMessage] = Try {
    copy(header = newHeader(key, value, overwrite).get)
  }

  override def removeHeader(keys: String*): FlowMessage = copy(header = doRemoveHeader(keys:_*))
}

case class TextFlowMessage(content : String, override val header: FlowMessageProps) extends FlowMessage(header) {
  override def body(): Any = content
  def getText(): String = content

  override def bodySize(): Int = content.length()

  override def withHeader(key: String, value: Any, overwrite: Boolean = true): Try[FlowMessage] = Try {
    copy(header = newHeader(key, value, overwrite).get)
  }

  override def removeHeader(keys: String*): FlowMessage = copy(header = doRemoveHeader(keys:_*))
}
