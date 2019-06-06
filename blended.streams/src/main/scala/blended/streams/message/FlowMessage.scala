package blended.streams.message

import akka.NotUsed
import akka.util.ByteString

import scala.reflect.ClassTag
import scala.util.Try

sealed trait MsgProperty {
  def value : Any
  override def toString : String = value.toString
}

case class StringMsgProperty(override val value : String) extends MsgProperty {
  override def toString : String = "\"" + super.toString + "\""
}

case class UnitMsgProperty(override val value : Unit = ()) extends MsgProperty
case class IntMsgProperty(override val value : Int) extends MsgProperty
case class LongMsgProperty(override val value : Long) extends MsgProperty
case class BooleanMsgProperty(override val value : Boolean) extends MsgProperty
case class ByteMsgProperty(override val value : Byte) extends MsgProperty
case class ShortMsgProperty(override val value : Short) extends MsgProperty
case class FloatMsgProperty(override val value : Float) extends MsgProperty
case class DoubleMsgProperty(override val value : Double) extends MsgProperty

object MsgProperty {

  import scala.language.implicitConversions

  def apply() : MsgProperty = UnitMsgProperty()
  def apply(s : String) : MsgProperty = StringMsgProperty(s)
  def apply(i : Int) : MsgProperty = IntMsgProperty(i)
  def apply(l : Long) : MsgProperty = LongMsgProperty(l)
  def apply(b : Boolean) : MsgProperty = BooleanMsgProperty(b)
  def apply(b : Byte) : MsgProperty = ByteMsgProperty(b)
  def apply(s : Short) : MsgProperty = ShortMsgProperty(s)
  def apply(f : Float) : MsgProperty = FloatMsgProperty(f)
  def apply(d : Double) : MsgProperty = DoubleMsgProperty(d)

  def lift(v : Any) : Try[MsgProperty] = Try {
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
          case _                     => throw new IllegalArgumentException(s"Unsupported Msg Property type [${o.getClass().getName()}]")
        }
    }
  }

  def unapply(p : MsgProperty) : Any = p.value
}

object FlowMessage {

  type FlowMessageProps = Map[String, MsgProperty]

  def props(m : (String, Any)*) : Try[FlowMessageProps] = Try {
    m.map {
      case (k, v) =>
        val p : MsgProperty = MsgProperty.lift(v).get
        k -> p
    }.toMap
  }

  val noProps : FlowMessageProps = Map.empty[String, MsgProperty]

  def apply(props : FlowMessageProps) : FlowMessage = BaseFlowMessage(props)
  def apply(content : String)(props : FlowMessageProps) : FlowMessage = TextFlowMessage(content, props)
  def apply(content : ByteString)(props : FlowMessageProps) : FlowMessage = BinaryFlowMessage(content, props)
  def apply(content : Array[Byte])(props : FlowMessageProps) : FlowMessage = BinaryFlowMessage(content, props)

}

import blended.streams.message.FlowMessage.FlowMessageProps

sealed abstract class FlowMessage(msgHeader : FlowMessageProps) {

  def body() : Any
  def clearBody() : FlowMessage = this

  def header : FlowMessageProps = msgHeader

  def bodySize() : Int

  def header[T](name : String)(implicit m : Manifest[T]) : Option[T] = {

    case class ByteMsgProperty(override val value : Byte) extends MsgProperty
    case class FloatMsgProperty(override val value : Float) extends MsgProperty
    case class DoubleMsgProperty(override val value : Double) extends MsgProperty

    def fromString[T](v : String)(implicit clazz : ClassTag[T]) : Option[T] = clazz.runtimeClass match {
      case c if c == classOf[Short]   => Some(v.toShort.asInstanceOf[T])
      case c if c == classOf[Int]     => Some(v.toInt.asInstanceOf[T])
      case c if c == classOf[Long]    => Some(v.toLong.asInstanceOf[T])
      case c if c == classOf[Boolean] => Some(v.toBoolean.asInstanceOf[T])
      case c if c == classOf[Byte]    => Some(v.toByte.asInstanceOf[T])
      case c if c == classOf[Float]   => Some(v.toFloat.asInstanceOf[T])
      case c if c == classOf[Double]  => Some(v.toDouble.asInstanceOf[T])
      case c if c == classOf[Unit]    => Some(().asInstanceOf[T])
      case _                          => None
    }

    def classMatch(v : Any, clazz : Class[_]) : Boolean = {

      val intClasses : Seq[Class[_]] = Seq(classOf[Integer], classOf[Int])
      val longClasses : Seq[Class[_]] = Seq(classOf[java.lang.Long], classOf[Long])
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
      case Some(v) if classMatch(v.value, m.runtimeClass) =>
        Some(v.value.asInstanceOf[T])

      case Some(v) if v.isInstanceOf[StringMsgProperty] =>
        fromString(v.value.toString)

      case Some(v) if v.isInstanceOf[UnitMsgProperty] && m.runtimeClass.getName().equals("void") =>
        Some(v.value.asInstanceOf[T])

      case _ =>
        None
    }
  }

  def headerWithDefault[T](name : String, default : T)(implicit m : Manifest[T]) : T = header[T](name) match {
    case Some(v) => v
    case None    => default
  }

  def removeHeader(keys : String*) : FlowMessage

  def withHeaders(header : FlowMessageProps) : Try[FlowMessage] = Try {
    header.foldLeft(this) {
      case (current, (key, prop)) =>
        current.withHeader(key, prop.value).get
    }
  }

  def withHeader(key : String, value : Any, overwrite : Boolean = true) : Try[FlowMessage]

  protected def doRemoveHeader(keys : String*) : FlowMessageProps = header.filterKeys(k => !keys.contains(k))

  protected def newHeader(key : String, value : Any, overwrite : Boolean) : Try[FlowMessageProps] = Try {
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

  override def toString : String = s"${getClass().getSimpleName()}(content-size [${bodySize()}])($header)"
}

case class BaseFlowMessage(override val header : FlowMessageProps) extends FlowMessage(header) {
  override def body() : Any = NotUsed

  override def bodySize() : Int = 0

  override def withHeader(key : String, value : Any, overwrite : Boolean = true) : Try[FlowMessage] = Try {
    copy(header = newHeader(key, value, overwrite).get)
  }

  override def removeHeader(keys : String*) : FlowMessage = copy(header = doRemoveHeader(keys : _*))
}

object BinaryFlowMessage {

  def apply(bytes : Array[Byte], msgHeader : FlowMessageProps) : BinaryFlowMessage = BinaryFlowMessage(ByteString(bytes), msgHeader)

  //TODO: Initialize from Byte Stream
}

case class BinaryFlowMessage(content : ByteString, override val header : FlowMessageProps) extends FlowMessage(header) {
  override def body() : Any = content

  def getBytes() : ByteString = content

  override def bodySize() : Int = content.size

  override def withHeader(key : String, value : Any, overwrite : Boolean = true) : Try[FlowMessage] = Try {
    copy(header = newHeader(key, value, overwrite).get)
  }

  override def removeHeader(keys : String*) : FlowMessage = copy(header = doRemoveHeader(keys : _*))

  override def clearBody() : FlowMessage = BinaryFlowMessage(Array.empty[Byte], header)
}

case class TextFlowMessage(content : String, override val header : FlowMessageProps) extends FlowMessage(header) {

  private val textContent : Option[String] = Option(content)

  // scalastyle:off null
  override def body() : Any = textContent.orNull
  // scalastyle:on null

  def getText() : String = textContent.getOrElse("")

  override def bodySize() : Int = getText().length()

  override def withHeader(key : String, value : Any, overwrite : Boolean = true) : Try[FlowMessage] = Try {
    copy(header = newHeader(key, value, overwrite).get)
  }

  override def removeHeader(keys : String*) : FlowMessage = copy(header = doRemoveHeader(keys : _*))

  // scalastyle:off null
  override def clearBody() : FlowMessage = TextFlowMessage("", header)
  // scalastyle:on null
}
