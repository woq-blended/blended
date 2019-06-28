package blended.streams.message

import akka.NotUsed
import akka.util.ByteString

import scala.reflect.ClassTag
import scala.runtime.BoxedUnit
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

  def apply(o: Any) : Try[MsgProperty] = Try {
    val resMap : Map[Class[_], Any => MsgProperty] = Map(
      classOf[Unit] -> (_ => UnitMsgProperty()),
      classOf[Short] -> (v => ShortMsgProperty(v.asInstanceOf[Short])),
      classOf[Int] -> (v => IntMsgProperty(v.asInstanceOf[Int])),
      classOf[Long] -> (v => LongMsgProperty(v.asInstanceOf[Long])),
      classOf[Boolean] -> (v => BooleanMsgProperty(v.asInstanceOf[Boolean])),
      classOf[Byte] -> (v => ByteMsgProperty(v.asInstanceOf[Byte])),
      classOf[Float] -> (v => FloatMsgProperty(v.asInstanceOf[Float])),
      classOf[Double] -> (v => DoubleMsgProperty(v.asInstanceOf[Double])),
      classOf[String] -> (v => StringMsgProperty(v.toString())),
      classOf[BoxedUnit] -> (_ => UnitMsgProperty()),
      classOf[java.lang.Integer] -> (v => IntMsgProperty(v.asInstanceOf[Int])),
      classOf[java.lang.Long] -> (v => LongMsgProperty(v.asInstanceOf[Long])),
      classOf[java.lang.Short] -> (v => ShortMsgProperty(v.asInstanceOf[Short])),
      classOf[java.lang.Float] -> (v => FloatMsgProperty(v.asInstanceOf[Float])),
      classOf[java.lang.Double] -> (v => DoubleMsgProperty(v.asInstanceOf[Double])),
      classOf[java.lang.Boolean] -> (v => BooleanMsgProperty(v.asInstanceOf[Boolean])),
      classOf[java.lang.Byte] -> (v => ByteMsgProperty(v.asInstanceOf[Byte]))
    )

    val v : Any = Option(o).getOrElse(())
    resMap.get(v.getClass()).map(f => f(v)) match {
      case None => throw new IllegalArgumentException(s"Unsupported Msg Property type [${v.getClass().getName()}]")
      case Some(p) => p
    }
  }

  def unapply(p : MsgProperty) : Any = p.value
}

object FlowMessage {

  type FlowMessageProps = Map[String, MsgProperty]

  def props(m : (String, Any)*) : Try[FlowMessageProps] = Try {
    m.map {
      case (k, v) =>
        val p : MsgProperty = MsgProperty(v).get
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

  private def classMatch(v : Any, clazz : Class[_]) : Boolean = {

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

    v.getClass() == clazz || matches.get(v.getClass()).exists { clazzes =>
      clazzes.exists(c => v.getClass().isAssignableFrom(c))
    }
  }

  private def fromString[T](v : String)(implicit clazz : ClassTag[T]) : Option[T] = {

    val resMap : Map[Class[_], String => Any] = Map (
      classOf[Short] -> (s => s.toShort),
      classOf[Int] -> (s => s.toInt),
      classOf[Long] -> (s => s.toLong),
      classOf[Boolean] -> (s => s.toBoolean),
      classOf[Byte] -> (s => s.toByte),
      classOf[Float] -> (s => s.toFloat),
      classOf[Double] -> (s => s.toDouble),
      classOf[Unit] -> (_ => Unit)
    )

    resMap.get(clazz.runtimeClass).map(_(v)).map(_.asInstanceOf[T])
  }

  def header[T](name : String)(implicit m : Manifest[T]) : Option[T] = {

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
      header.filterKeys(_ != key) + (key -> MsgProperty(value).get)
    } else {
      if (header.isDefinedAt(key)) {
        header
      } else {
        header + (key -> MsgProperty(value).get)
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

  override def body() : Any = textContent.orNull

  def getText() : String = textContent.getOrElse("")

  override def bodySize() : Int = getText().length()

  override def withHeader(key : String, value : Any, overwrite : Boolean = true) : Try[FlowMessage] = Try {
    copy(header = newHeader(key, value, overwrite).get)
  }

  override def removeHeader(keys : String*) : FlowMessage = copy(header = doRemoveHeader(keys : _*))

  override def clearBody() : FlowMessage = TextFlowMessage("", header)
}
