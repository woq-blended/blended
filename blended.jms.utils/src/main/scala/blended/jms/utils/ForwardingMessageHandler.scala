package blended.jms.utils

import javax.jms._
import scala.collection.JavaConverters._

import org.slf4j.LoggerFactory

class ForwardingMessageHandler(cf: ConnectionFactory, destName: String, additionalHeader : Map[String, AnyRef] = Map.empty)
  extends JMSMessageHandler
  with JMSSupport
  with JMSMessageFactory {

  private[this] val log = LoggerFactory.getLogger(classOf[ForwardingMessageHandler])

  override def createMessage(session: Session, content: Option[Any]): Message = content match {
    case None => throw new Exception("No Message to forward")
    case Some(m) if m.isInstanceOf[Message] =>
      val original = m.asInstanceOf[Message]

      val result = original match {
        case tMsg : TextMessage =>
          session.createTextMessage(tMsg.getText())
        case bMsg : BytesMessage =>
          val r = session.createBytesMessage()
          val bytes = new Array[Byte](bMsg.getBodyLength().toInt)
          bMsg.readBytes(bytes)
          r.writeBytes(bytes)
          r
        case pMsg =>
          log.warn(s"Message [${pMsg.getJMSMessageID()}] is of type [${pMsg.getClass().getName()}], forwarding as plain message")
          session.createMessage()
      }

      original.getPropertyNames().asScala.filter{ name =>
        !(name.toString().startsWith("JMS"))
      }.foreach { name =>
        result.setObjectProperty(name.toString(), original.getObjectProperty(name.toString()))
      }

      additionalHeader.foreach { case (k,v) => result.setObjectProperty(k,v) }

      result.setJMSCorrelationID(original.getJMSCorrelationID())
      result

    case Some(o) => throw new Exception(s"[$o] is not of type [${classOf[Message].getName()}]")
  }

  override def handleMessage(msg: Message): Option[Throwable] = {

    sendMessage(
      cf = cf,
      destName = destName,
      content = Some(msg),
      msgFactory = this,
      deliveryMode = msg.getJMSDeliveryMode(),
      priority = msg.getJMSPriority(),
      ttl = if (msg.getJMSExpiration() > 0) msg.getJMSExpiration() - System.currentTimeMillis() else 0
    )
  }
}
